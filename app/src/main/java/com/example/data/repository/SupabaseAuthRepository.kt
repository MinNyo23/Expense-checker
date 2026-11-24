@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.example.data.repository

import com.example.data.model.AuthSessionInfo
import com.example.data.model.AuthState
import com.example.data.supabase.SupabaseClientProvider
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Concrete authentication repository backed by the official Supabase Kotlin Auth SDK (`auth-kt`).
 *
 * Responsibilities:
 * - Sign-up, sign-in, sign-out, password reset, and password update via Supabase Auth.
 * - Session management (`getCurrentSession`, `refreshOrRestoreSession`, `observeSessionStatus`).
 * - Observable `authState: StateFlow<AuthState>` and `observeAuthState()` for Jetpack Compose UI.
 */
class SupabaseAuthRepository(
    private val clientProvider: SupabaseClientProvider,
    private val profileRepository: ProfileRepository,
    autoObserveSdkSession: Boolean = true
) : AuthRepository(
    authDataSource = SupabaseAuthDataSource(clientProvider),
    profileRepository = profileRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        if (autoObserveSdkSession && clientProvider.isConfigured) {
            scope.launch {
                clientProvider.auth.sessionStatus.collect { status ->
                    when (status) {
                        is SessionStatus.Initializing -> {
                            if (_authState.value is AuthState.SignedOut) {
                                _authState.value = AuthState.SigningIn
                            }
                        }
                        is SessionStatus.RefreshFailure -> {
                            _authState.value = AuthState.SessionExpired()
                        }
                        is SessionStatus.NotAuthenticated -> {
                            val current = _authState.value
                            if (current is AuthState.SignedIn || current is AuthState.AccountNotLinked || current is AuthState.SigningIn) {
                                _authState.value = AuthState.SignedOut
                            }
                        }
                        is SessionStatus.Authenticated -> {
                            val current = _authState.value
                            val uid = status.session.user?.id.orEmpty()
                            if (uid.isNotBlank() && (current !is AuthState.SignedIn || current.authUserId != uid)) {
                                refreshOrRestoreSession()
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Observes the underlying Supabase Auth SDK `SessionStatus` mapped into `RawSessionStatus`.
     */
    fun observeSessionStatus(): Flow<RawSessionStatus> =
        if (!clientProvider.isConfigured) {
            flowOf(RawSessionStatus.NotAuthenticated)
        } else {
            clientProvider.auth.sessionStatus.map { status ->
                when (status) {
                    is SessionStatus.Initializing -> RawSessionStatus.Initializing
                    is SessionStatus.RefreshFailure -> RawSessionStatus.RefreshFailed
                    is SessionStatus.NotAuthenticated -> RawSessionStatus.NotAuthenticated
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
                }
            }
        }

    /**
     * Convenience accessor exposing the reactive `StateFlow<AuthState>` directly for UI observation.
     */
    fun observeUiAuthState(): StateFlow<AuthState> = authState

    /**
     * Checks whether the Supabase Auth SDK currently holds an active authenticated session.
     */
    fun hasActiveSdkSession(): Boolean =
        clientProvider.isConfigured && clientProvider.auth.currentSessionOrNull() != null
}
