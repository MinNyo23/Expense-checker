package com.example.data.repository

import com.example.data.model.AuthResult
import com.example.data.model.AuthSessionInfo
import com.example.data.model.AuthState
import com.example.data.model.SupabaseDomainError
import com.example.data.model.SupabaseErrorMapper
import com.example.data.supabase.SupabaseClientProvider
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

sealed interface AuthSignUpOutcome {
    data class Authenticated(val session: AuthSessionInfo) : AuthSignUpOutcome
    data class EmailConfirmationPending(val email: String) : AuthSignUpOutcome
}

sealed interface RawSessionStatus {
    data object Initializing : RawSessionStatus
    data class Authenticated(val session: AuthSessionInfo) : RawSessionStatus
    data object NotAuthenticated : RawSessionStatus
    data object RefreshFailed : RawSessionStatus
}

interface AuthDataSource {
    val isConfigured: Boolean
    val rawSessionStatus: Flow<RawSessionStatus>
    suspend fun signUp(email: String, password: String): AuthSignUpOutcome
    suspend fun signIn(email: String, password: String): AuthSessionInfo
    suspend fun signOut()
    suspend fun resetPassword(email: String)
    suspend fun updatePassword(newPassword: String)
    suspend fun getCurrentSession(): AuthSessionInfo?
    suspend fun refreshCurrentSession(): AuthSessionInfo?
}

class SupabaseAuthDataSource(
    private val clientProvider: SupabaseClientProvider
) : AuthDataSource {

    override val isConfigured: Boolean
        get() = clientProvider.isConfigured

    override val rawSessionStatus: Flow<RawSessionStatus>
        get() = clientProvider.auth.sessionStatus.map { status ->
            when (status) {
                is SessionStatus.Initializing -> RawSessionStatus.Initializing
                is SessionStatus.Authenticated -> {
                    val user = status.session.user
                    val uid = user?.id.orEmpty()
                    if (uid.isNotBlank()) {
                        RawSessionStatus.Authenticated(
                            AuthSessionInfo(
                                authUserId = uid,
                                email = user?.email.orEmpty(),
                                expiresAtEpochSec = status.session.expiresAt.epochSeconds
                            )
                        )
                    } else {
                        RawSessionStatus.NotAuthenticated
                    }
                }
                is SessionStatus.RefreshFailure -> RawSessionStatus.RefreshFailed
                is SessionStatus.NotAuthenticated -> RawSessionStatus.NotAuthenticated
            }
        }

    override suspend fun signUp(email: String, password: String): AuthSignUpOutcome = withContext(Dispatchers.IO) {
        val result = clientProvider.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        val activeSession = clientProvider.auth.currentSessionOrNull()
        val uid = activeSession?.user?.id ?: result?.id.orEmpty()
        if (activeSession != null && uid.isNotBlank()) {
            AuthSignUpOutcome.Authenticated(
                AuthSessionInfo(
                    authUserId = uid,
                    email = activeSession.user?.email ?: email,
                    expiresAtEpochSec = activeSession.expiresAt.epochSeconds
                )
            )
        } else {
            AuthSignUpOutcome.EmailConfirmationPending(email)
        }
    }

    override suspend fun signIn(email: String, password: String): AuthSessionInfo = withContext(Dispatchers.IO) {
        clientProvider.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        val session = clientProvider.auth.currentSessionOrNull()
            ?: throw SupabaseDomainError.AuthenticationRequired()
        val uid = session.user?.id.orEmpty()
        if (uid.isBlank()) {
            throw SupabaseDomainError.AuthenticationRequired()
        }
        AuthSessionInfo(
            authUserId = uid,
            email = session.user?.email ?: email,
            expiresAtEpochSec = session.expiresAt.epochSeconds
        )
    }

    override suspend fun signOut() = withContext(Dispatchers.IO) {
        clientProvider.auth.signOut()
    }

    override suspend fun resetPassword(email: String) = withContext(Dispatchers.IO) {
        clientProvider.auth.resetPasswordForEmail(email)
    }

    override suspend fun updatePassword(newPassword: String): Unit = withContext(Dispatchers.IO) {
        clientProvider.auth.updateUser {
            this.password = newPassword
        }
        Unit
    }

    override suspend fun getCurrentSession(): AuthSessionInfo? = withContext(Dispatchers.IO) {
        val session = clientProvider.auth.currentSessionOrNull() ?: return@withContext null
        val uid = session.user?.id.orEmpty()
        if (uid.isBlank()) return@withContext null
        AuthSessionInfo(
            authUserId = uid,
            email = session.user?.email.orEmpty(),
            expiresAtEpochSec = session.expiresAt.epochSeconds
        )
    }

    override suspend fun refreshCurrentSession(): AuthSessionInfo? = withContext(Dispatchers.IO) {
        clientProvider.auth.refreshCurrentSession()
        getCurrentSession()
    }
}

/**
 * Manages Supabase Auth lifecycle, profile linkage verification, and reactive AuthState.
 * Does NOT touch legacy user_accounts or bypass RLS.
 */
class AuthRepository(
    private val authDataSource: AuthDataSource,
    private val profileRepository: ProfileRepository
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.SignedOut)

    fun observeAuthState(): Flow<AuthState> = _authState.asStateFlow()

    val currentAuthState: AuthState
        get() = _authState.value

    suspend fun getCurrentSession(): AuthSessionInfo? = authDataSource.getCurrentSession()

    suspend fun signUp(email: String, password: String): Result<AuthResult> {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            return Result.failure(SupabaseDomainError.ValidationError("Please enter a valid email address."))
        }
        if (password.length < 6) {
            return Result.failure(SupabaseDomainError.ValidationError("Password must be at least 6 characters."))
        }
        if (!authDataSource.isConfigured) {
            val state = AuthState.NetworkError("Supabase backend is not configured yet.")
            _authState.value = state
            return Result.failure(SupabaseDomainError.NetworkUnavailable())
        }

        _authState.value = AuthState.SigningIn
        return try {
            when (val outcome = authDataSource.signUp(cleanEmail, password)) {
                is AuthSignUpOutcome.EmailConfirmationPending -> {
                    val state = AuthState.EmailConfirmationRequired(
                        email = outcome.email,
                        message = "Check ${outcome.email} and confirm your email address, then sign in."
                    )
                    _authState.value = state
                    Result.success(
                        AuthResult(
                            success = true,
                            email = outcome.email,
                            message = state.message,
                            requiresEmailConfirmation = true
                        )
                    )
                }
                is AuthSignUpOutcome.Authenticated -> {
                    val nextState = resolveProfileAndBuildState(outcome.session)
                    _authState.value = nextState
                    toAuthResult(nextState)
                }
            }
        } catch (e: Exception) {
            val mapped = SupabaseErrorMapper.mapError(e)
            _authState.value = mapDomainErrorToAuthState(mapped, cleanEmail)
            Result.failure(mapped)
        }
    }

    suspend fun signIn(email: String, password: String): Result<AuthResult> {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            return Result.failure(SupabaseDomainError.ValidationError("Please enter a valid email address."))
        }
        if (password.isBlank()) {
            return Result.failure(SupabaseDomainError.ValidationError("Please enter your password."))
        }
        if (!authDataSource.isConfigured) {
            _authState.value = AuthState.NetworkError("Supabase backend is not configured yet.")
            return Result.failure(SupabaseDomainError.NetworkUnavailable())
        }

        _authState.value = AuthState.SigningIn
        return try {
            val session = authDataSource.signIn(cleanEmail, password)
            val nextState = resolveProfileAndBuildState(session)
            _authState.value = nextState
            toAuthResult(nextState)
        } catch (e: Exception) {
            val mapped = SupabaseErrorMapper.mapError(e)
            _authState.value = mapDomainErrorToAuthState(mapped, cleanEmail)
            Result.failure(mapped)
        }
    }

    suspend fun signOut(): Result<Unit> {
        return try {
            runCatching { authDataSource.signOut() }
            _authState.value = AuthState.SignedOut
            Result.success(Unit)
        } catch (e: Exception) {
            _authState.value = AuthState.SignedOut
            Result.success(Unit)
        }
    }

    suspend fun resetPassword(email: String): Result<Unit> {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            return Result.failure(SupabaseDomainError.ValidationError("Please enter a valid email address."))
        }
        return try {
            authDataSource.resetPassword(cleanEmail)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(SupabaseErrorMapper.mapError(e))
        }
    }

    suspend fun updatePassword(newPassword: String): Result<Unit> {
        if (newPassword.length < 6) {
            return Result.failure(SupabaseDomainError.ValidationError("New password must be at least 6 characters."))
        }
        if (getCurrentSession() == null) {
            return Result.failure(SupabaseDomainError.AuthenticationRequired())
        }
        return try {
            authDataSource.updatePassword(newPassword)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(SupabaseErrorMapper.mapError(e))
        }
    }

    /**
     * Restores and validates the current Supabase session on app startup or manual re-check.
     * Refreshes an expired or near-expired session if possible.
     */
    suspend fun refreshOrRestoreSession(): AuthState {
        if (!authDataSource.isConfigured) {
            _authState.value = AuthState.SignedOut
            return AuthState.SignedOut
        }
        _authState.value = AuthState.SigningIn
        return try {
            val current = authDataSource.getCurrentSession()
            if (current == null) {
                _authState.value = AuthState.SignedOut
                return AuthState.SignedOut
            }
            val nowSec = System.currentTimeMillis() / 1000L
            val activeSession = if (current.expiresAtEpochSec > 0L && current.expiresAtEpochSec <= nowSec + 60L) {
                authDataSource.refreshCurrentSession()
            } else {
                current
            }
            if (activeSession == null) {
                val expired = AuthState.SessionExpired()
                _authState.value = expired
                return expired
            }
            val resolved = resolveProfileAndBuildState(activeSession)
            _authState.value = resolved
            resolved
        } catch (e: Exception) {
            val mapped = SupabaseErrorMapper.mapError(e)
            val state = when (mapped) {
                is SupabaseDomainError.SessionExpired,
                is SupabaseDomainError.AuthenticationRequired -> AuthState.SessionExpired(mapped.userMessage)
                is SupabaseDomainError.NetworkUnavailable -> AuthState.NetworkError(mapped.userMessage)
                else -> AuthState.SessionExpired(mapped.userMessage)
            }
            _authState.value = state
            state
        }
    }

    private suspend fun resolveProfileAndBuildState(session: AuthSessionInfo): AuthState {
        val profileRes = profileRepository.loadVerifiedProfile(session.authUserId)
        return profileRes.fold(
            onSuccess = { profile ->
                if (profile == null) {
                    AuthState.AccountNotLinked(
                        authUserId = session.authUserId,
                        email = session.email
                    )
                } else {
                    AuthState.SignedIn(
                        authUserId = session.authUserId,
                        email = profile.email.ifBlank { session.email },
                        coupleId = profile.couple_id,
                        userName = profile.user_name,
                        expiresAtEpochSec = session.expiresAtEpochSec
                    )
                }
            },
            onFailure = { error ->
                val mapped = SupabaseErrorMapper.mapError(error)
                mapDomainErrorToAuthState(mapped, session.email)
            }
        )
    }

    private fun mapDomainErrorToAuthState(error: SupabaseDomainError, email: String): AuthState {
        return when (error) {
            is SupabaseDomainError.InvalidCredentials -> AuthState.InvalidCredentials(error.userMessage)
            is SupabaseDomainError.EmailConfirmationRequired -> AuthState.EmailConfirmationRequired(email, error.userMessage)
            is SupabaseDomainError.SessionExpired -> AuthState.SessionExpired(error.userMessage)
            is SupabaseDomainError.NetworkUnavailable -> AuthState.NetworkError(error.userMessage)
            is SupabaseDomainError.AccountNotLinked -> AuthState.AccountNotLinked("", email, error.userMessage)
            is SupabaseDomainError.AuthenticationRequired -> AuthState.SignedOut
            else -> AuthState.NetworkError(error.userMessage)
        }
    }

    private fun toAuthResult(state: AuthState): Result<AuthResult> {
        return when (state) {
            is AuthState.SignedIn -> Result.success(
                AuthResult(
                    success = true,
                    auth_user_id = state.authUserId,
                    email = state.email,
                    user_name = state.userName,
                    couple_id = state.coupleId,
                    message = "Signed in as ${state.userName.ifBlank { state.email }}"
                )
            )
            is AuthState.AccountNotLinked -> Result.success(
                AuthResult(
                    success = true,
                    auth_user_id = state.authUserId,
                    email = state.email,
                    message = state.message,
                    accountPendingLink = true
                )
            )
            is AuthState.EmailConfirmationRequired -> Result.success(
                AuthResult(
                    success = true,
                    email = state.email,
                    message = state.message,
                    requiresEmailConfirmation = true
                )
            )
            is AuthState.InvalidCredentials -> Result.failure(SupabaseDomainError.InvalidCredentials())
            is AuthState.SessionExpired -> Result.failure(SupabaseDomainError.SessionExpired())
            is AuthState.NetworkError -> Result.failure(SupabaseDomainError.NetworkUnavailable())
            else -> Result.failure(SupabaseDomainError.AuthenticationRequired())
        }
    }
}
