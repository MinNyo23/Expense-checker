package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CoupleAppContainer
import com.example.data.local.CouplePreferences
import com.example.data.local.DeviceHelper
import com.example.data.local.LocalCoupleCache
import com.example.data.local.UserSession
import com.example.data.location.LocationHelper
import com.example.data.location.LocationTrackingService
import com.example.data.model.AuthState
import com.example.data.model.CoupleMemo
import com.example.data.model.CoupleSyncStatus
import com.example.data.model.DeviceSession
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.LiveLocation
import com.example.data.model.LocationHistoryItem
import com.example.data.model.MoneyAccount
import com.example.data.model.MoneyAccountType
import com.example.data.model.MovieItem
import com.example.data.model.OwnerMoneySummary
import com.example.data.model.RecurringBill
import com.example.data.model.ResourceState
import com.example.data.model.SavingsGoal
import com.example.data.model.SplitType
import com.example.data.model.SupabaseDomainError
import com.example.data.model.SupabaseErrorMapper
import com.example.data.model.UserProfile
import com.example.data.repository.AuthRepository
import com.example.data.repository.ExpenseRepository
import com.example.data.repository.ImageStorageRepository
import com.example.data.repository.MemoRepository
import com.example.data.repository.ProfileRepository
import com.example.data.service.AccountLinkingService
import com.example.data.supabase.SupabaseClientManager
import com.example.data.supabase.SupabaseClientProvider
import com.example.data.supabase.SupabaseRepository
import com.example.util.FormatUtils
import com.example.util.LocationHistoryAnalyzer
import com.example.util.PermissionHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

data class CategorySummary(
    val category: ExpenseCategory,
    val totalAmount: Double,
    val count: Int,
    val percentage: Float
)

data class PartnerSpendingSummary(
    val partnerName: String,
    val amount: Double,
    val percentage: Float
)

data class DashboardSummary(
    val todayTotal: Double = 0.0,
    val monthTotal: Double = 0.0,
    val totalCount: Int = 0,
    val monthlyBudget: Double = 1000000.0,
    val budgetRemaining: Double = 1000000.0,
    val budgetUsedPercent: Float = 0f,
    val dailyPaceRemaining: Double = 0.0,
    val daysLeftInMonth: Int = 1,
    val netBalance: Double = 0.0,
    val balanceSummaryText: String = "All settled up! 🎉",
    val categories: List<CategorySummary> = emptyList(),
    val partnerBreakdown: List<PartnerSpendingSummary> = emptyList()
) {
    val budgetPercentage: Float get() = budgetUsedPercent
    val daysRemainingInMonth: Int get() = daysLeftInMonth
    val safeDailyPace: Double get() = dailyPaceRemaining
}

class CoupleExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = CoupleAppContainer.getPreferences(application)
    private val localCache = CoupleAppContainer.getLocalCache(application)
    val deviceHelper = DeviceHelper(application)
    val currentDeviceId: String = deviceHelper.getDeviceId()
    val currentDeviceName: String = deviceHelper.getDeviceName()
    val locationHelper = LocationHelper(application)
    val clientProvider: SupabaseClientProvider = CoupleAppContainer.getClientProvider()
    val profileRepository: ProfileRepository = CoupleAppContainer.getProfileRepository(application)
    val authRepository: AuthRepository = CoupleAppContainer.getAuthRepository(application)
    val imageStorageRepository: ImageStorageRepository = CoupleAppContainer.getImageStorageRepository(application)
    val expenseRepository: ExpenseRepository = CoupleAppContainer.getExpenseRepository(application)
    val memoRepository: MemoRepository = CoupleAppContainer.getMemoRepository(application)
    val clientManager = CoupleAppContainer.getClientManager(application)
    val repository = CoupleAppContainer.getRepository(application)
    val linkingService = CoupleAppContainer.getLinkingService(application)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val userSession: StateFlow<UserSession> = preferences.userSessionFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSession())

    val authState: StateFlow<AuthState> = authRepository.observeAuthState()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.SignedOut)

    val syncStatus: StateFlow<CoupleSyncStatus> = linkingService.syncStatus
    val isSyncing: StateFlow<Boolean> = linkingService.isSyncing

    private val _expensesState = MutableStateFlow<ResourceState<List<Expense>>>(ResourceState.Empty)
    val expensesState: StateFlow<ResourceState<List<Expense>>> = _expensesState.asStateFlow()

    val expenses: StateFlow<List<Expense>> = _expensesState
        .map { state -> if (state is ResourceState.Success) state.data else emptyList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val profiles: StateFlow<List<UserProfile>> = repository.profilesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _memosState = MutableStateFlow<ResourceState<List<CoupleMemo>>>(ResourceState.Empty)
    val memosState: StateFlow<ResourceState<List<CoupleMemo>>> = _memosState.asStateFlow()

    val memos: StateFlow<List<CoupleMemo>> = _memosState
        .map { state -> if (state is ResourceState.Success) state.data else emptyList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _signedMemoUrls = MutableStateFlow<Map<String, String>>(emptyMap())
    val signedMemoUrls: StateFlow<Map<String, String>> = _signedMemoUrls.asStateFlow()

    private val _memoImageErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val memoImageErrors: StateFlow<Map<String, String>> = _memoImageErrors.asStateFlow()

    val liveLocations: StateFlow<List<LiveLocation>> = repository.liveLocationsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val locationHistory: StateFlow<List<LocationHistoryItem>> = repository.locationHistoryFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val deviceSessions: StateFlow<List<DeviceSession>> = repository.deviceSessionsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val moneyAccounts: StateFlow<List<MoneyAccount>> = repository.moneyAccountsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val myMoneyAccounts: StateFlow<List<MoneyAccount>> = combine(moneyAccounts, userSession) { list, session ->
        list.filter { isAccountOwnedByMe(it, session) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val partnerMoneyAccounts: StateFlow<List<MoneyAccount>> = combine(moneyAccounts, userSession) { list, session ->
        list.filterNot { isAccountOwnedByMe(it, session) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val myMoneySummary: StateFlow<OwnerMoneySummary> = combine(myMoneyAccounts, userSession) { list, session ->
        OwnerMoneySummary.fromAccounts(
            ownerName = session.userName.ifBlank { "You" },
            accounts = list
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OwnerMoneySummary())

    val partnerMoneySummary: StateFlow<OwnerMoneySummary> = combine(
        partnerMoneyAccounts,
        userSession,
        profiles
    ) { list, session, profileList ->
        val pName = session.partnerName.ifBlank {
            profileList.firstOrNull { !it.user_name.equals(session.userName, ignoreCase = true) }?.user_name
                ?: list.firstOrNull()?.owner_name
                ?: "Girlfriend"
        }
        OwnerMoneySummary.fromAccounts(
            ownerName = pName,
            accounts = list
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OwnerMoneySummary(ownerName = "Girlfriend"))

    val myLiveLocation: StateFlow<LiveLocation?> = combine(liveLocations, userSession) { list, session ->
        list.firstOrNull { it.user_name.equals(session.userName, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val partnerLiveLocation: StateFlow<LiveLocation?> = combine(liveLocations, userSession) { list, session ->
        list.firstOrNull { !it.user_name.equals(session.userName, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val myProfile: StateFlow<UserProfile?> = combine(profiles, userSession) { profileList, session ->
        profileList.firstOrNull { it.user_name.equals(session.userName, ignoreCase = true) }
            ?: if (session.userName.isNotBlank()) {
                UserProfile(
                    id = "local-me",
                    couple_id = session.coupleId,
                    user_name = session.userName,
                    email = session.userEmail,
                    date_of_birth = session.dateOfBirth,
                    avatar_url = session.avatarPath
                )
            } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val partnerProfile: StateFlow<UserProfile?> = combine(profiles, userSession) { profileList, session ->
        val found = profileList.firstOrNull { !it.user_name.equals(session.userName, ignoreCase = true) }
        if (found != null) {
            found
        } else if (session.partnerName.isNotBlank() || session.partnerAvatarUrl.isNotBlank()) {
            UserProfile(
                id = "partner-cached",
                couple_id = session.coupleId,
                user_name = session.partnerName.ifBlank { "Partner" },
                email = session.partnerEmail,
                date_of_birth = session.partnerDob,
                avatar_url = session.partnerAvatarUrl
            )
        } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val savingsGoals: StateFlow<List<SavingsGoal>> = userSession.map { session ->
        if (session.savingsGoalsJson.isNotBlank()) {
            try {
                json.decodeFromString<List<SavingsGoal>>(session.savingsGoalsJson)
            } catch (e: Exception) {
                defaultSavingsGoals()
            }
        } else {
            defaultSavingsGoals()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), defaultSavingsGoals())

    val recurringBills: StateFlow<List<RecurringBill>> = userSession.map { session ->
        if (session.recurringBillsJson.isNotBlank()) {
            try {
                json.decodeFromString<List<RecurringBill>>(session.recurringBillsJson)
            } catch (e: Exception) {
                defaultRecurringBills()
            }
        } else {
            defaultRecurringBills()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), defaultRecurringBills())

    private val _movies = MutableStateFlow<List<MovieItem>>(emptyList())
    val movies: StateFlow<List<MovieItem>> = _movies.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isSessionInitializing = MutableStateFlow(true)
    val isSessionInitializing: StateFlow<Boolean> = _isSessionInitializing.asStateFlow()

    private val _isAppUnlocked = MutableStateFlow(false)
    val isAppUnlocked: StateFlow<Boolean> = _isAppUnlocked.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _currentUserCoordinates = MutableStateFlow<Pair<Double, Double>?>(null)
    val currentUserCoordinates: StateFlow<Pair<Double, Double>?> = _currentUserCoordinates.asStateFlow()

    // Calculated Dashboard summary with budget, pace, and split balances
    val dashboardSummary: StateFlow<DashboardSummary> = combine(
        expenses,
        userSession
    ) { expenseList, session ->
        calculateSummary(expenseList, session)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummary())

    init {
        // 1. Restore or refresh official Supabase Auth session and resolve verified profile on startup
        viewModelScope.launch {
            try {
                val initialSession = preferences.userSessionFlow.first()
                if (!initialSession.isPinSet) {
                    _isAppUnlocked.value = true
                }
                if (initialSession.movieWatchlistJson.isNotBlank()) {
                    runCatching {
                        json.decodeFromString<List<MovieItem>>(initialSession.movieWatchlistJson)
                    }.onSuccess { restored ->
                        _movies.value = restored
                    }
                }
                authRepository.refreshOrRestoreSession()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("CoupleExpenseViewModel", "Session init warning: ${e.javaClass.simpleName}")
            } finally {
                _isSessionInitializing.value = false
            }
        }

        // Keep movies synchronized with persisted DataStore watchlist updates
        viewModelScope.launch {
            userSession
                .map { it.movieWatchlistJson }
                .distinctUntilChanged()
                .collect { rawJson ->
                    if (rawJson.isNotBlank()) {
                        runCatching { json.decodeFromString<List<MovieItem>>(rawJson) }
                            .onSuccess { decoded -> _movies.value = decoded }
                    }
                }
        }

        // 2. React to verified AuthState transitions (RLS-scoped loading)
        viewModelScope.launch {
            authState.collect { state ->
                when (state) {
                    is AuthState.SignedIn -> {
                        val existingSession = userSession.value
                        val resolvedName = state.userName.ifBlank {
                            existingSession.userName.ifBlank { state.email.substringBefore("@") }
                        }
                        preferences.saveSession(
                            userName = resolvedName,
                            coupleId = state.coupleId,
                            coupleCode = existingSession.coupleCode,
                            authUserId = state.authUserId
                        )
                        repository.hydrateFromLocalCache(state.coupleId)
                        val cachedMovies = localCache.getSnapshot(state.coupleId)?.movies.orEmpty()
                        if (cachedMovies.isNotEmpty() && _movies.value.isEmpty()) {
                            _movies.value = cachedMovies
                            preferences.updateMovieWatchlist(json.encodeToString(cachedMovies))
                        }
                        repository.initCoupleSession(state.coupleId)
                        launch { loadCoupleExpenses() }
                        launch { loadCoupleMemos() }
                        launch {
                            profileRepository.getCoupleProfiles(state.coupleId)
                        }
                    }
                    is AuthState.AccountNotLinked -> {
                        repository.clearSessionState(clearDiskCache = false)
                        _expensesState.value = ResourceState.Empty
                        _memosState.value = ResourceState.Empty
                        _signedMemoUrls.value = emptyMap()
                        _memoImageErrors.value = emptyMap()
                    }
                    is AuthState.SignedOut,
                    is AuthState.SessionExpired -> {
                        _expensesState.value = ResourceState.Empty
                        _memosState.value = ResourceState.Empty
                        _signedMemoUrls.value = emptyMap()
                        _memoImageErrors.value = emptyMap()
                    }
                    else -> Unit
                }
            }
        }

        viewModelScope.launch {
            userSession
                .map { !it.isPinSet }
                .distinctUntilChanged()
                .collect { pinDisabled ->
                    if (pinDisabled) {
                        _isAppUnlocked.value = true
                    }
                }
        }

        // Keep local user & partner profiles in sync with remote profiles when restored/updated
        viewModelScope.launch {
            profiles.collect { list ->
                val session = userSession.value
                if (!session.isPaired || session.coupleId.isBlank()) return@collect

                val me = list.firstOrNull { profile ->
                    (session.userEmail.isNotBlank() && profile.email.equals(session.userEmail, ignoreCase = true)) ||
                        (session.userName.isNotBlank() && profile.user_name.equals(session.userName, ignoreCase = true))
                }
                if (me != null) {
                    val resolvedName = session.userName.ifBlank { me.user_name }
                    val resolvedEmail = session.userEmail.ifBlank { me.email }
                    val resolvedDob = session.dateOfBirth.ifBlank { me.date_of_birth }
                    val resolvedAvatar = session.avatarPath.ifBlank { me.avatar_url }
                    if (resolvedName != session.userName ||
                        resolvedEmail != session.userEmail ||
                        resolvedDob != session.dateOfBirth ||
                        resolvedAvatar != session.avatarPath
                    ) {
                        preferences.updateUserProfile(
                            name = resolvedName,
                            email = resolvedEmail,
                            dob = resolvedDob,
                            avatarPath = resolvedAvatar
                        )
                    }
                }

                val partner = list.firstOrNull { profile ->
                    if (me != null && profile.id == me.id) false
                    else if (session.userEmail.isNotBlank() && profile.email.isNotBlank() && profile.email.equals(session.userEmail, ignoreCase = true)) false
                    else !profile.user_name.equals(session.userName, ignoreCase = true)
                }
                if (partner != null && (
                        partner.user_name != session.partnerName ||
                        partner.email != session.partnerEmail ||
                        partner.date_of_birth != session.partnerDob ||
                        partner.avatar_url != session.partnerAvatarUrl
                    )
                ) {
                    preferences.updatePartnerProfile(
                        name = partner.user_name,
                        email = partner.email,
                        dob = partner.date_of_birth,
                        avatarUrl = partner.avatar_url
                    )
                }
            }
        }

        // Keep linking & sync participant status up-to-date cleanly without duplicate emissions
        viewModelScope.launch {
            combine(userSession, profiles, deviceSessions) { session, profileList, deviceList ->
                val names = buildList {
                    if (session.userName.isNotBlank()) add(session.userName)
                    if (session.partnerName.isNotBlank()) add(session.partnerName)
                    profileList.forEach { if (it.user_name.isNotBlank()) add(it.user_name) }
                    deviceList.forEach { if (it.user_name.isNotBlank()) add(it.user_name) }
                }
                Triple(session.coupleId, session.coupleCode, names)
            }.distinctUntilChanged().collect { (coupleId, coupleCode, names) ->
                if (coupleId.isNotBlank()) {
                    linkingService.updateSyncParticipants(coupleId, coupleCode, names)
                }
            }
        }

        // Live location tracking loop + Background Service observer
        viewModelScope.launch {
            userSession
                .map { Triple(it.isLocationSharingEnabled, it.isPaired, it.coupleId) }
                .distinctUntilChanged()
                .collect { (enabled, paired, coupleId) ->
                    val app = getApplication<Application>()
                    if (enabled && paired && coupleId.isNotBlank() && PermissionHelper.hasLocationPermission(app)) {
                        LocationTrackingService.start(app)
                        startLocationTrackingLoop()
                    } else {
                        locationTrackingJob?.cancel()
                        locationTrackingJob = null
                        LocationTrackingService.stop(app)
                    }
                }
        }
    }

    fun dismissMessage() {
        _userMessage.value = null
    }

    fun showMessage(message: String) {
        _userMessage.value = message
    }

    fun signIn(
        email: String,
        password: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            onResult(false, "Please enter a valid email address.")
            return
        }
        if (password.isBlank()) {
            onResult(false, "Please enter your password.")
            return
        }

        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                val result = authRepository.signIn(cleanEmail, password)
                _isRefreshing.value = false

                result.onSuccess { auth ->
                    if (auth.accountPendingLink) {
                        onResult(true, auth.message)
                    } else {
                        _userMessage.value = "Welcome back, ${auth.user_name.ifBlank { auth.email }}!"
                        onResult(true, auth.message.ifBlank { "Signed in successfully!" })
                    }
                }.onFailure { err ->
                    onResult(false, SupabaseErrorMapper.toUserMessage(err))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _isRefreshing.value = false
                onResult(false, SupabaseErrorMapper.toUserMessage(e))
            }
        }
    }

    fun signUp(
        email: String,
        password: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            onResult(false, "Please enter a valid email address.")
            return
        }
        if (password.length < 6) {
            onResult(false, "Password must be at least 6 characters.")
            return
        }

        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                val result = authRepository.signUp(cleanEmail, password)
                _isRefreshing.value = false

                result.onSuccess { auth ->
                    if (auth.requiresEmailConfirmation) {
                        onResult(true, auth.message)
                    } else if (auth.accountPendingLink) {
                        onResult(true, auth.message)
                    } else {
                        _userMessage.value = "Account signed in as ${auth.user_name.ifBlank { auth.email }}!"
                        onResult(true, auth.message.ifBlank { "Account created!" })
                    }
                }.onFailure { err ->
                    onResult(false, SupabaseErrorMapper.toUserMessage(err))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _isRefreshing.value = false
                onResult(false, SupabaseErrorMapper.toUserMessage(e))
            }
        }
    }

    fun verifySignupOtp(
        email: String,
        token: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = authRepository.verifySignupOtp(email, token)
            _isRefreshing.value = false
            result.onSuccess { auth ->
                onResult(true, auth.message.ifBlank { "Email verified successfully." })
            }.onFailure { err ->
                onResult(false, SupabaseErrorMapper.toUserMessage(err))
            }
        }
    }

    fun resendSignupOtp(
        email: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val result = authRepository.resendSignupOtp(email)
            result.onSuccess {
                onResult(true, "A new verification code was sent. Check your inbox and spam folder.")
            }.onFailure { err ->
                onResult(false, SupabaseErrorMapper.toUserMessage(err))
            }
        }
    }

    fun recheckAccountProfileLink(onComplete: (AuthState) -> Unit = {}) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val state = authRepository.refreshOrRestoreSession()
            _isRefreshing.value = false
            if (state is AuthState.AccountNotLinked) {
                _userMessage.value = "Your profile is still pending link on the backend."
            } else if (state is AuthState.SignedIn) {
                _userMessage.value = "Profile linked! Welcome back, ${state.userName.ifBlank { state.email }}."
            }
            onComplete(state)
        }
    }

    fun resetPasswordEmail(
        email: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            onResult(false, "Please enter your registered email address.")
            return
        }
        viewModelScope.launch {
            _isRefreshing.value = true
            val res = authRepository.resetPassword(cleanEmail)
            _isRefreshing.value = false
            res.onSuccess {
                val msg = "Password reset link sent to $cleanEmail. Check your inbox."
                _userMessage.value = msg
                onResult(true, msg)
            }.onFailure { err ->
                onResult(false, SupabaseErrorMapper.toUserMessage(err))
            }
        }
    }

    fun updateAuthPassword(
        newPassword: String,
        confirmPassword: String,
        onResult: (Boolean, String) -> Unit
    ) {
        if (newPassword.length < 6) {
            onResult(false, "New password must be at least 6 characters.")
            return
        }
        if (newPassword != confirmPassword) {
            onResult(false, "New password and confirmation do not match.")
            return
        }
        viewModelScope.launch {
            _isRefreshing.value = true
            val res = authRepository.updatePassword(newPassword)
            _isRefreshing.value = false
            res.onSuccess {
                _userMessage.value = "Account password updated securely."
                onResult(true, "Account password updated!")
            }.onFailure { err ->
                onResult(false, SupabaseErrorMapper.toUserMessage(err))
            }
        }
    }

    suspend fun loadCoupleExpenses() {
        _expensesState.value = ResourceState.Loading
        val res = expenseRepository.getExpenses()
        _expensesState.value = res.fold(
            onSuccess = { list -> if (list.isEmpty()) ResourceState.Empty else ResourceState.Success(list) },
            onFailure = { err -> ResourceState.Error(SupabaseErrorMapper.toUserMessage(err)) }
        )
    }

    fun retryExpenses() {
        viewModelScope.launch { loadCoupleExpenses() }
    }

    suspend fun loadCoupleMemos() {
        _memosState.value = ResourceState.Loading
        val res = memoRepository.getMemos()
        _memosState.value = res.fold(
            onSuccess = { list -> if (list.isEmpty()) ResourceState.Empty else ResourceState.Success(list) },
            onFailure = { err -> ResourceState.Error(SupabaseErrorMapper.toUserMessage(err)) }
        )
    }

    fun retryMemos() {
        viewModelScope.launch { loadCoupleMemos() }
    }

    fun resolveMemoImageUrl(objectPath: String, forceRefresh: Boolean = false) {
        val cleanPath = objectPath.trim()
        if (cleanPath.isBlank()) return
        if (!forceRefresh && _signedMemoUrls.value.containsKey(cleanPath)) return

        viewModelScope.launch {
            val res = imageStorageRepository.getSignedUrl(cleanPath, forceRefresh)
            res.onSuccess { url ->
                _signedMemoUrls.value = _signedMemoUrls.value + (cleanPath to url)
                _memoImageErrors.value = _memoImageErrors.value - cleanPath
            }.onFailure { err ->
                _memoImageErrors.value = _memoImageErrors.value + (cleanPath to SupabaseErrorMapper.toUserMessage(err))
            }
        }
    }

    fun addExpense(
        amount: Double,
        category: ExpenseCategory,
        rawNote: String,
        splitType: SplitType,
        photoPath: String?,
        date: String,
        latitude: Double?,
        longitude: Double?,
        loggedBy: String?,
        moneyAccountId: String? = null,
        onResult: (Boolean) -> Unit
    ) {
        val state = authState.value
        if (state !is AuthState.SignedIn || state.coupleId.isBlank()) {
            _userMessage.value = "Authentication required. Please sign in."
            onResult(false)
            return
        }

        val session = userSession.value
        val author = if (!loggedBy.isNullOrBlank()) loggedBy else state.userName.ifBlank { session.userName.ifBlank { "You" } }
        val selectedAccount = moneyAccountId?.takeIf { it.isNotBlank() }?.let { accId ->
            moneyAccounts.value.firstOrNull { it.id == accId }
        }
        val encodedNote = Expense.buildNote(
            userNote = rawNote,
            splitType = splitType,
            photoPath = photoPath,
            accountId = selectedAccount?.id,
            accountLabel = selectedAccount?.shortBadgeLabel
        )

        val newExpense = Expense(
            id = UUID.randomUUID().toString(),
            couple_id = state.coupleId,
            user_name = author,
            amount = amount,
            category = category.name,
            date = date,
            note = encodedNote,
            latitude = latitude,
            longitude = longitude,
            created_at = SupabaseRepository.getTodayIso()
        )

        viewModelScope.launch {
            _isRefreshing.value = true
            val result = expenseRepository.addExpense(newExpense)
            if (result.isSuccess) {
                val created = result.getOrThrow()
                val currentList = (_expensesState.value as? ResourceState.Success)?.data.orEmpty()
                val updatedList = listOf(created) + currentList.filterNot { it.id == created.id }
                _expensesState.value = ResourceState.Success(updatedList)
                if (selectedAccount != null) {
                    repository.adjustMoneyAccountBalance(
                        accountId = selectedAccount.id,
                        coupleId = state.coupleId,
                        deltaAmount = -amount
                    )
                }
            }
            _isRefreshing.value = false

            result.onSuccess {
                val acctMsg = selectedAccount?.let { " from ${it.shortBadgeLabel}" }.orEmpty()
                _userMessage.value = "Added ${FormatUtils.formatMmk(amount)} (${category.title})$acctMsg"
                onResult(true)
            }.onFailure { err ->
                _userMessage.value = SupabaseErrorMapper.toUserMessage(err)
                onResult(false)
            }
        }
    }

    fun updateMonthlyBudget(newBudget: Double) {
        viewModelScope.launch {
            preferences.updateMonthlyBudget(newBudget)
            _userMessage.value = "Monthly budget updated to ${FormatUtils.formatMmk(newBudget)}"
        }
    }

    fun updateLoveStartDate(date: String) {
        viewModelScope.launch {
            preferences.updateRelationshipStartDate(date)
            _userMessage.value = "Anniversary date updated!"
        }
    }

    fun deleteExpense(expenseId: String) {
        val state = authState.value
        if (state !is AuthState.SignedIn) {
            _userMessage.value = "Authentication required. Please sign in."
            return
        }
        val existing = expenses.value.firstOrNull { it.id == expenseId }
        viewModelScope.launch {
            val res = expenseRepository.deleteExpense(expenseId, state.coupleId)
            res.onSuccess {
                val remaining = expenses.value.filterNot { it.id == expenseId }
                _expensesState.value = if (remaining.isEmpty()) ResourceState.Empty else ResourceState.Success(remaining)
                val linkedAccountId = existing?.moneyAccountId
                if (!linkedAccountId.isNullOrBlank() && existing.amount > 0.0) {
                    repository.adjustMoneyAccountBalance(
                        accountId = linkedAccountId,
                        coupleId = state.coupleId,
                        deltaAmount = existing.amount
                    )
                    _userMessage.value = "Expense deleted & ${FormatUtils.formatMmk(existing.amount)} refunded to ${existing.moneyAccountLabel ?: "account"}"
                } else {
                    _userMessage.value = "Expense deleted"
                }
            }.onFailure { err ->
                _userMessage.value = SupabaseErrorMapper.toUserMessage(err)
            }
        }
    }

    // --- Separated My Money & Partner Money Wallet Actions ---

    internal fun isAccountOwnedByMe(account: MoneyAccount, session: UserSession): Boolean {
        val myEmail = session.userEmail.trim()
        val partnerEmail = session.partnerEmail.trim()
        val accEmail = account.owner_email.trim()
        if (accEmail.isNotBlank()) {
            if (myEmail.isNotBlank() && accEmail.equals(myEmail, ignoreCase = true)) {
                return true
            }
            if (partnerEmail.isNotBlank() && accEmail.equals(partnerEmail, ignoreCase = true)) {
                return false
            }
        }
        val accOwner = account.owner_name.trim()
        val cleanPartnerName = session.partnerName.trim()
        val myAliases = listOf(session.userName, session.username, "You")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (cleanPartnerName.isNotBlank() &&
            accOwner.equals(cleanPartnerName, ignoreCase = true) &&
            myAliases.none { it.equals(cleanPartnerName, ignoreCase = true) }
        ) {
            return false
        }
        return account.matchesOwner(
            targetName = session.userName.ifBlank { "You" },
            targetEmail = myEmail,
            extraNames = listOf(session.username, "You")
        )
    }

    fun addMoneyAccount(
        type: MoneyAccountType,
        accountName: String,
        institutionName: String,
        last4: String,
        initialBalance: Double,
        note: String = "",
        forPartner: Boolean = false,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val session = userSession.value
        if (!session.isPaired || session.coupleId.isBlank()) {
            onResult(false, "Please pair your couple workspace first")
            return
        }

        val cleanName = accountName.trim()
        val cleanInst = institutionName.trim()
        if (cleanName.isBlank() && cleanInst.isBlank()) {
            onResult(false, "Please enter an account or wallet name")
            return
        }

        val resolvedPartnerName = session.partnerName.ifBlank {
            partnerProfile.value?.user_name?.ifBlank { "Girlfriend" } ?: "Girlfriend"
        }
        val ownerName = if (forPartner) resolvedPartnerName else session.userName.ifBlank { "You" }
        val ownerEmail = if (forPartner) {
            session.partnerEmail.takeIf { it.isNotBlank() && !it.equals(session.userEmail, ignoreCase = true) }.orEmpty()
        } else {
            session.userEmail
        }

        val finalAccountName = cleanName.ifBlank { cleanInst }
        val cleanLast4 = last4.filter { it.isDigit() }.takeLast(4)

        val newAccount = MoneyAccount(
            id = UUID.randomUUID().toString(),
            couple_id = session.coupleId,
            owner_name = ownerName,
            owner_email = ownerEmail,
            account_type = type.name,
            account_name = finalAccountName,
            institution_name = cleanInst,
            account_number_last4 = cleanLast4,
            balance = initialBalance,
            initial_balance = initialBalance,
            note = note.trim(),
            updated_at = System.currentTimeMillis()
        )

        viewModelScope.launch {
            _isRefreshing.value = true
            val res = repository.upsertMoneyAccount(newAccount)
            _isRefreshing.value = false
            res.onSuccess { saved ->
                _userMessage.value = "Added ${saved.displayTitle} for $ownerName (${FormatUtils.formatMmk(saved.balance)})"
                onResult(true, "Account added!")
            }.onFailure { err ->
                onResult(false, err.message ?: "Failed to add account")
            }
        }
    }

    fun updateMoneyAccount(
        account: MoneyAccount,
        accountName: String,
        institutionName: String,
        last4: String,
        newBalance: Double,
        note: String = "",
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val cleanName = accountName.trim().ifBlank { institutionName.trim().ifBlank { account.account_name } }
        val updated = account.copy(
            account_name = cleanName,
            institution_name = institutionName.trim(),
            account_number_last4 = last4.filter { it.isDigit() }.takeLast(4),
            balance = newBalance,
            note = note.trim(),
            updated_at = System.currentTimeMillis()
        )
        viewModelScope.launch {
            _isRefreshing.value = true
            val res = repository.upsertMoneyAccount(updated)
            _isRefreshing.value = false
            res.onSuccess { saved ->
                _userMessage.value = "Updated ${saved.displayTitle} (${FormatUtils.formatMmk(saved.balance)})"
                onResult(true, "Account updated")
            }.onFailure { err ->
                onResult(false, err.message ?: "Failed to update account")
            }
        }
    }

    fun topUpOrAdjustMoneyAccount(
        account: MoneyAccount,
        amount: Double,
        isAbsoluteBalance: Boolean,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val targetBalance = if (isAbsoluteBalance) amount else account.balance + amount
        val updated = account.copy(
            balance = targetBalance,
            updated_at = System.currentTimeMillis()
        )
        viewModelScope.launch {
            val res = repository.upsertMoneyAccount(updated)
            res.onSuccess { saved ->
                val verb = if (isAbsoluteBalance) "Balance synced to" else if (amount >= 0) "Deposited" else "Adjusted"
                _userMessage.value = "$verb ${FormatUtils.formatMmk(if (isAbsoluteBalance) saved.balance else kotlin.math.abs(amount))} • ${saved.shortBadgeLabel}"
                onResult(true, "Balance updated")
            }.onFailure { err ->
                onResult(false, err.message ?: "Failed to update balance")
            }
        }
    }

    fun transferBetweenAccounts(
        fromAccountId: String,
        toAccountId: String,
        amount: Double,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val session = userSession.value
        if (session.coupleId.isBlank()) {
            onResult(false, "Couple session not active")
            return
        }
        val list = moneyAccounts.value
        val fromAcc = list.firstOrNull { it.id == fromAccountId }
        val toAcc = list.firstOrNull { it.id == toAccountId }
        if (fromAcc == null || toAcc == null) {
            onResult(false, "Please select both source and destination accounts")
            return
        }
        val fromIsMe = isAccountOwnedByMe(fromAcc, session)
        val toIsMe = isAccountOwnedByMe(toAcc, session)
        if (fromIsMe != toIsMe) {
            onResult(false, "Transfers stay within the same person's separate money accounts")
            return
        }

        viewModelScope.launch {
            val res = repository.transferBetweenMoneyAccounts(fromAccountId, toAccountId, amount, session.coupleId)
            res.onSuccess { (fromUpdated, toUpdated) ->
                _userMessage.value = "Moved ${FormatUtils.formatMmk(amount)}: ${fromUpdated.shortBadgeLabel} → ${toUpdated.shortBadgeLabel}"
                onResult(true, "Transfer completed")
            }.onFailure { err ->
                onResult(false, err.message ?: "Transfer failed")
            }
        }
    }

    fun deleteMoneyAccount(accountId: String) {
        val session = userSession.value
        val target = moneyAccounts.value.firstOrNull { it.id == accountId }
        viewModelScope.launch {
            repository.deleteMoneyAccount(accountId, session.coupleId)
            _userMessage.value = "Removed ${target?.displayTitle ?: "account"}"
        }
    }

    fun settleUpBalance(amount: Double, onComplete: () -> Unit = {}) {
        val state = authState.value
        val session = userSession.value
        if (state !is AuthState.SignedIn || state.coupleId.isBlank() || amount <= 0.0) return

        val myName = state.userName.ifBlank { session.userName.ifBlank { "You" } }
        val currentNet = dashboardSummary.value.netBalance
        val resolvedPartner = session.partnerName.ifBlank {
            partnerProfile.value?.user_name.orEmpty()
        }
        val settlementPayer = resolveSettlementPayer(
            currentNetBalance = currentNet,
            myName = myName,
            partnerName = resolvedPartner
        )

        val settlementExpense = Expense(
            id = UUID.randomUUID().toString(),
            couple_id = state.coupleId,
            user_name = settlementPayer,
            amount = amount,
            category = ExpenseCategory.OTHER.name,
            date = SupabaseRepository.getTodayDate(),
            note = Expense.buildNote("Settle Up Payment", SplitType.SETTLEMENT),
            created_at = SupabaseRepository.getTodayIso()
        )

        viewModelScope.launch {
            val res = expenseRepository.addExpense(settlementExpense)
            res.onSuccess { created ->
                val currentList = (_expensesState.value as? ResourceState.Success)?.data.orEmpty()
                _expensesState.value = ResourceState.Success(listOf(created) + currentList.filterNot { it.id == created.id })
                _userMessage.value = "Settled up ${FormatUtils.formatMmk(amount)}! Balance squared"
                onComplete()
            }.onFailure { err ->
                _userMessage.value = SupabaseErrorMapper.toUserMessage(err)
            }
        }
    }

    // Savings Goals Methods
    fun addSavingsGoal(title: String, targetAmount: Double, targetDate: String, icon: String) {
        val currentList = savingsGoals.value.toMutableList()
        val newGoal = SavingsGoal(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            targetAmount = targetAmount,
            currentAmount = 0.0,
            targetDate = targetDate,
            categoryIcon = icon
        )
        currentList.add(newGoal)
        saveSavingsGoalsList(currentList)
        _userMessage.value = "New Savings Goal created: $title 🎯"
    }

    fun depositToSavingsGoal(goalId: String, depositAmount: Double, logAsExpense: Boolean, payerName: String) {
        val currentList = savingsGoals.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == goalId }
        if (index != -1) {
            val goal = currentList[index]
            val updated = goal.copy(currentAmount = goal.currentAmount + depositAmount)
            currentList[index] = updated
            saveSavingsGoalsList(currentList)

            if (logAsExpense) {
                addExpense(
                    amount = depositAmount,
                    category = ExpenseCategory.GIFTS,
                    rawNote = "Deposit to goal: ${goal.title} 🐷",
                    splitType = SplitType.SHARED,
                    photoPath = null,
                    date = SupabaseRepository.getTodayDate(),
                    latitude = null,
                    longitude = null,
                    loggedBy = payerName,
                    onResult = {}
                )
            }
            _userMessage.value = "Deposited ${FormatUtils.formatMmk(depositAmount)} to ${goal.title}! 💕"
        }
    }

    fun deleteSavingsGoal(goalId: String) {
        val currentList = savingsGoals.value.filter { it.id != goalId }
        saveSavingsGoalsList(currentList)
        _userMessage.value = "Savings goal removed"
    }

    private fun saveSavingsGoalsList(list: List<SavingsGoal>) {
        viewModelScope.launch {
            val encoded = json.encodeToString(list)
            preferences.updateSavingsGoals(encoded)
        }
    }

    // Recurring Bills Methods
    fun addRecurringBill(title: String, amount: Double, category: String, dueDay: Int, payer: String) {
        val currentList = recurringBills.value.toMutableList()
        val newBill = RecurringBill(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            amount = amount,
            category = category,
            dueDayOfMonth = dueDay,
            defaultPayer = payer
        )
        currentList.add(newBill)
        saveRecurringBillsList(currentList)
        _userMessage.value = "Added bill: $title (due day $dueDay)"
    }

    fun deleteRecurringBill(billId: String) {
        val currentList = recurringBills.value.filter { it.id != billId }
        saveRecurringBillsList(currentList)
        _userMessage.value = "Recurring bill removed"
    }

    fun logRecurringBill(bill: RecurringBill) = logRecurringBillAsExpense(bill)

    fun logRecurringBillAsExpense(bill: RecurringBill) {
        val cat = ExpenseCategory.fromString(bill.category)
        addExpense(
            amount = bill.amount,
            category = cat,
            rawNote = "Monthly Bill: ${bill.title} 🔄",
            splitType = SplitType.SHARED,
            photoPath = null,
            date = SupabaseRepository.getTodayDate(),
            latitude = null,
            longitude = null,
            loggedBy = bill.defaultPayer.ifBlank { null },
            onResult = { success ->
                if (success) {
                    _userMessage.value = "Logged bill '${bill.title}' for this month!"
                }
            }
        )
    }

    private fun saveRecurringBillsList(list: List<RecurringBill>) {
        viewModelScope.launch {
            val encoded = json.encodeToString(list)
            preferences.updateRecurringBills(encoded)
        }
    }

    // Movie Watch Later & Finished List Methods
    fun addMovieToWatchLater(title: String, note: String = "") {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) {
            _userMessage.value = "Please enter a movie name."
            return
        }
        val session = userSession.value
        val newMovie = MovieItem(
            id = UUID.randomUUID().toString(),
            title = cleanTitle,
            isWatched = false,
            addedBy = session.userName.ifBlank { "You" },
            note = note.trim(),
            addedAt = SupabaseRepository.getTodayDate(),
            watchedAt = ""
        )
        val updatedList = listOf(newMovie) + _movies.value
        persistMoviesList(updatedList)
        _userMessage.value = "Added '$cleanTitle' to Watch Later 🎬"
    }

    fun markMovieAsWatched(movieId: String) {
        val currentList = _movies.value
        val target = currentList.firstOrNull { it.id == movieId } ?: return
        val updatedMovie = target.copy(
            isWatched = true,
            watchedAt = SupabaseRepository.getTodayDate()
        )
        val updatedList = listOf(updatedMovie) + currentList.filterNot { it.id == movieId }
        persistMoviesList(updatedList)
        _userMessage.value = "Moved '${target.title}' to Finished List ✅"
    }

    fun moveMovieToWatchLater(movieId: String) {
        val currentList = _movies.value
        val target = currentList.firstOrNull { it.id == movieId } ?: return
        val updatedMovie = target.copy(
            isWatched = false,
            watchedAt = ""
        )
        val updatedList = listOf(updatedMovie) + currentList.filterNot { it.id == movieId }
        persistMoviesList(updatedList)
        _userMessage.value = "Moved '${target.title}' back to Watch Later 🎬"
    }

    fun deleteMovie(movieId: String) {
        val updatedList = _movies.value.filterNot { it.id == movieId }
        persistMoviesList(updatedList)
        _userMessage.value = "Movie removed from list"
    }

    private fun persistMoviesList(list: List<MovieItem>) {
        _movies.value = list
        val coupleId = userSession.value.coupleId
        viewModelScope.launch {
            val encoded = json.encodeToString(list)
            preferences.updateMovieWatchlist(encoded)
            if (coupleId.isNotBlank()) {
                localCache.updateSnapshot(coupleId) { it.copy(movies = list) }
            }
        }
    }

    fun refresh() {
        val state = authState.value
        val session = userSession.value
        if (state is AuthState.SignedIn && state.coupleId.isNotBlank()) {
            viewModelScope.launch {
                _isRefreshing.value = true
                loadCoupleExpenses()
                loadCoupleMemos()
                val names = buildList {
                    if (session.userName.isNotBlank()) add(session.userName)
                    if (session.partnerName.isNotBlank()) add(session.partnerName)
                    profiles.value.forEach { add(it.user_name) }
                }
                linkingService.syncIfChanged(
                    coupleId = state.coupleId,
                    coupleCode = session.coupleCode,
                    memberNames = names
                )
                _isRefreshing.value = false
            }
        }
    }

    fun syncNow() {
        val state = authState.value
        val session = userSession.value
        if (state !is AuthState.SignedIn || state.coupleId.isBlank()) {
            _userMessage.value = "Please sign in first"
            return
        }
        viewModelScope.launch {
            _isRefreshing.value = true
            loadCoupleExpenses()
            loadCoupleMemos()
            val names = buildList {
                if (session.userName.isNotBlank()) add(session.userName)
                if (session.partnerName.isNotBlank()) add(session.partnerName)
                profiles.value.forEach { add(it.user_name) }
            }
            linkingService.syncLinkedUsersData(
                coupleId = state.coupleId,
                coupleCode = session.coupleCode,
                memberNames = names
            )
            _isRefreshing.value = false
            _userMessage.value = "All data synchronized with Supabase!"
        }
    }

    fun generateUniqueSixDigitLinkCode(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val session = userSession.value
        if (session.coupleId.isBlank()) {
            onResult(false, "Please sign in first")
            return
        }
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = linkingService.generateUniqueLinkCode(
                coupleId = session.coupleId,
                userEmail = session.userEmail,
                userName = session.userName.ifBlank { "You" }
            )
            _isRefreshing.value = false
            result.onSuccess { res ->
                preferences.saveSession(
                    userName = session.userName,
                    coupleId = res.couple_id,
                    coupleCode = res.link_code
                )
                registerCurrentDeviceInBackend(
                    userName = session.userName.ifBlank { "You" },
                    coupleId = res.couple_id,
                    coupleCode = res.link_code,
                    email = session.userEmail
                )
                _userMessage.value = "New unique 6-digit code ready: ${res.link_code}"
                onResult(true, res.link_code)
            }.onFailure { err ->
                val msg = err.message ?: "Could not generate 6-digit code"
                _userMessage.value = msg
                onResult(false, msg)
            }
        }
    }

    fun linkPartnerWithSixDigitCode(
        code: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val cleanCode = code.trim()
        if (!linkingService.isValidSixDigitCode(cleanCode) && cleanCode.length < 4) {
            onResult(false, "Please enter a valid 6-digit link code")
            return
        }
        val session = userSession.value
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = linkingService.linkAccountWithCode(
                userEmail = session.userEmail,
                userName = session.userName.ifBlank { "Partner" },
                currentCoupleId = session.coupleId,
                linkCode = cleanCode
            )
            _isRefreshing.value = false
            result.onSuccess { auth ->
                preferences.saveSession(
                    userName = auth.user_name.ifBlank { session.userName },
                    coupleId = auth.couple_id,
                    coupleCode = auth.couple_code
                )
                repository.initCoupleSession(auth.couple_id)
                registerCurrentDeviceInBackend(
                    userName = auth.user_name.ifBlank { session.userName },
                    coupleId = auth.couple_id,
                    coupleCode = auth.couple_code,
                    email = session.userEmail
                )
                syncNow()
                _userMessage.value = "Linked with partner (${auth.couple_code}) & synced all data! 💕"
                onResult(true, "Linked & synced with couple code ${auth.couple_code}!")
            }.onFailure { err ->
                val msg = err.message ?: "Failed to link partner account"
                _userMessage.value = msg
                onResult(false, msg)
            }
        }
    }

    fun getActiveSupabaseHost(): String = clientProvider.host.ifBlank { clientManager.getActiveHost() }

    fun testSupabaseConnection(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val (ok, msg) = clientProvider.testConnection()
            onResult(ok, msg)
        }
    }

    fun logout(onComplete: () -> Unit = {}) {
        val coupleId = userSession.value.coupleId
        viewModelScope.launch {
            try {
                locationTrackingJob?.cancel()
                locationTrackingJob = null
                liveGpsStreamJob?.cancel()
                liveGpsStreamJob = null
                runCatching { LocationTrackingService.stop(getApplication()) }

                kotlinx.coroutines.withTimeoutOrNull(1500L) {
                    runCatching { repository.removeDeviceSession(currentDeviceId, coupleId) }
                }
                authRepository.signOut()
                runCatching { clientManager.stopRealtime() }
                // Preserve local disk cache while clearing in-memory state and local session prefs
                repository.clearSessionState(coupleIdToClearCache = coupleId, clearDiskCache = false)
                _expensesState.value = ResourceState.Empty
                _memosState.value = ResourceState.Empty
                _movies.value = emptyList()
                _signedMemoUrls.value = emptyMap()
                _memoImageErrors.value = emptyMap()
                preferences.clearSession(clearProfileDetails = true)
                _isAppUnlocked.value = true
                _userMessage.value = "Signed out safely."
            } finally {
                onComplete()
            }
        }
    }

    fun disconnectCouple() = logout()

    fun revokeDeviceSession(deviceId: String) {
        val coupleId = userSession.value.coupleId
        viewModelScope.launch {
            if (deviceId == currentDeviceId) {
                logout()
            } else {
                repository.removeDeviceSession(deviceId, coupleId)
                _userMessage.value = "Device access removed from backend"
            }
        }
    }

    private suspend fun registerCurrentDeviceInBackend(
        userName: String,
        coupleId: String,
        coupleCode: String,
        email: String = ""
    ) {
        if (coupleId.isBlank()) return
        val deviceSession = DeviceSession(
            device_id = currentDeviceId,
            couple_id = coupleId,
            couple_code = coupleCode,
            user_name = userName,
            email = email,
            device_name = currentDeviceName,
            is_active = true,
            last_login_at = SupabaseRepository.getTodayIso()
        )
        repository.saveDeviceSession(deviceSession)
    }

    fun unlockApp(pin: String): Boolean {
        val actualPin = userSession.value.appPin
        return if (actualPin.isBlank() || pin == actualPin) {
            _isAppUnlocked.value = true
            true
        } else {
            false
        }
    }

    fun lockApp() {
        if (userSession.value.isPinSet) {
            _isAppUnlocked.value = false
        }
    }

    fun updateAppPin(newPin: String) {
        viewModelScope.launch {
            preferences.updateAppPin(newPin.trim())
            _isAppUnlocked.value = true
            if (newPin.isBlank()) {
                _userMessage.value = "PIN lock disabled"
            } else {
                _userMessage.value = "4-Digit PIN Security Shield enabled! 🔒"
            }
        }
    }

    fun updateProfile(
        name: String,
        email: String,
        dob: String,
        avatarUri: Uri?,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val signedIn = authState.value as? AuthState.SignedIn
        val session = userSession.value
        val cleanName = name.trim().ifBlank { session.userName.ifBlank { "You" } }
        val cleanEmail = email.trim()
        val cleanDob = dob.trim()

        viewModelScope.launch {
            _isRefreshing.value = true

            var finalAvatarPath = myProfile.value?.avatar_url?.ifBlank { session.avatarPath } ?: session.avatarPath
            if (avatarUri != null) {
                val uploadRes = imageStorageRepository.uploadFromUri(getApplication(), avatarUri)
                if (uploadRes.isFailure) {
                    _isRefreshing.value = false
                    val errMsg = SupabaseErrorMapper.toUserMessage(uploadRes.exceptionOrNull()!!)
                    _userMessage.value = errMsg
                    onComplete(false, errMsg)
                    return@launch
                }
                finalAvatarPath = uploadRes.getOrThrow()
            }

            preferences.updateUserProfile(
                name = cleanName,
                email = cleanEmail,
                dob = cleanDob,
                avatarPath = finalAvatarPath
            )

            val currentRemoteProfile = myProfile.value
            if (signedIn != null &&
                currentRemoteProfile != null &&
                !currentRemoteProfile.id.startsWith("local-") &&
                currentRemoteProfile.auth_user_id == signedIn.authUserId
            ) {
                val res = profileRepository.updateOwnProfile(
                    profile = currentRemoteProfile,
                    authUserId = signedIn.authUserId,
                    newName = cleanName,
                    newDob = cleanDob,
                    newAvatarUrl = finalAvatarPath
                )
                _isRefreshing.value = false
                res.onSuccess {
                    _userMessage.value = "Profile updated and synchronized!"
                    onComplete(true, "Profile updated and synchronized!")
                }.onFailure { err ->
                    val errMsg = SupabaseErrorMapper.toUserMessage(err)
                    _userMessage.value = errMsg
                    onComplete(false, errMsg)
                }
            } else {
                _isRefreshing.value = false
                _userMessage.value = "Profile saved locally"
                onComplete(true, "Profile saved locally")
            }
        }
    }

    fun changePasswordOrPin(
        oldPin: String,
        newPin: String,
        confirmPin: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        val currentPin = userSession.value.appPin
        if (currentPin.isNotBlank() && oldPin.trim() != currentPin) {
            onComplete(false, "Current PIN / Password is incorrect")
            return
        }

        val cleanNew = newPin.trim()
        val cleanConfirm = confirmPin.trim()

        if (cleanNew.length != 4 || !cleanNew.all { it.isDigit() }) {
            onComplete(false, "New PIN must be exactly 4 numeric digits")
            return
        }

        if (cleanNew != cleanConfirm) {
            onComplete(false, "New PIN and confirmation do not match")
            return
        }

        viewModelScope.launch {
            preferences.updateAppPin(cleanNew)
            _isAppUnlocked.value = true
            _userMessage.value = "Security PIN successfully updated! 🔒"
            onComplete(true, "Security PIN updated successfully!")
        }
    }

    fun updateScreenSecurity(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateScreenSecurity(enabled)
            _userMessage.value = if (enabled) "Screen privacy enabled (Hidden from app switcher)" else "Screen privacy disabled"
        }
    }

    fun addMemo(
        title: String,
        note: String,
        imageUri: Uri?,
        date: String,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val state = authState.value
        if (state !is AuthState.SignedIn || state.coupleId.isBlank()) {
            onComplete(false, SupabaseDomainError.AuthenticationRequired().userMessage)
            return
        }
        if (title.isBlank() && note.isBlank()) {
            onComplete(false, "Please enter a memo title or note.")
            return
        }

        val cleanDate = date.ifBlank { SupabaseRepository.getTodayDate() }

        viewModelScope.launch {
            _isRefreshing.value = true

            var uploadedObjectPath = ""
            if (imageUri != null) {
                val uploadResult = imageStorageRepository.uploadFromUri(getApplication(), imageUri)
                if (uploadResult.isFailure) {
                    _isRefreshing.value = false
                    val errMsg = SupabaseErrorMapper.toUserMessage(uploadResult.exceptionOrNull()!!)
                    _userMessage.value = errMsg
                    onComplete(false, errMsg)
                    return@launch
                }
                uploadedObjectPath = uploadResult.getOrThrow()
            }

            val res = memoRepository.createMemo(
                title = title,
                note = note,
                date = cleanDate,
                imagePath = uploadedObjectPath
            )
            _isRefreshing.value = false

            res.onSuccess { created ->
                val currentList = (_memosState.value as? ResourceState.Success)?.data.orEmpty()
                _memosState.value = ResourceState.Success(listOf(created) + currentList.filterNot { it.id == created.id })
                if (created.image_url.isNotBlank()) {
                    resolveMemoImageUrl(created.image_url)
                }
                _userMessage.value = "Love Memo saved & synced with your couple!"
                onComplete(true, "Memo added successfully!")
            }.onFailure { err ->
                // Clean up uploaded private object if database insert failed to avoid orphaned storage objects
                if (uploadedObjectPath.isNotBlank()) {
                    runCatching { imageStorageRepository.deleteImage(uploadedObjectPath) }
                }
                val errMsg = SupabaseErrorMapper.toUserMessage(err)
                _userMessage.value = errMsg
                onComplete(false, errMsg)
            }
        }
    }

    fun deleteMemo(memoId: String) {
        val target = memos.value.firstOrNull { it.id == memoId } ?: return
        deleteMemo(target)
    }

    fun deleteMemo(memo: CoupleMemo) {
        viewModelScope.launch {
            val res = memoRepository.deleteMemo(memo)
            res.onSuccess {
                val remaining = memos.value.filterNot { it.id == memo.id }
                _memosState.value = if (remaining.isEmpty()) ResourceState.Empty else ResourceState.Success(remaining)
                if (memo.image_url.isNotBlank()) {
                    _signedMemoUrls.value = _signedMemoUrls.value - memo.image_url
                    _memoImageErrors.value = _memoImageErrors.value - memo.image_url
                }
                _userMessage.value = "Memo removed"
            }.onFailure { err ->
                _userMessage.value = SupabaseErrorMapper.toUserMessage(err)
            }
        }
    }

    internal fun resolveSettlementPayer(
        currentNetBalance: Double,
        myName: String,
        partnerName: String
    ): String {
        val cleanMe = myName.ifBlank { "You" }
        val cleanPartner = partnerName.ifBlank { "Partner" }.let { candidate ->
            if (candidate.equals(cleanMe, ignoreCase = true)) "$cleanMe Partner" else candidate
        }
        return if (currentNetBalance > 0) cleanPartner else cleanMe
    }

    internal fun calculateSummary(list: List<Expense>, session: UserSession): DashboardSummary {
        val todayStr = SupabaseRepository.getTodayDate()
        val currentMonthPrefix = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val myName = session.userName.ifBlank { "You" }

        var todayTotal = 0.0
        var monthTotal = 0.0

        val categorySums = mutableMapOf<ExpenseCategory, Double>()
        val categoryCounts = mutableMapOf<ExpenseCategory, Int>()
        val partnerSums = mutableMapOf<String, Double>()

        var netDebt = 0.0 // positive: partner owes user; negative: user owes partner

        for (exp in list) {
            val amount = exp.amount
            if (exp.date == todayStr) {
                todayTotal += amount
            }
            if (exp.date.startsWith(currentMonthPrefix)) {
                monthTotal += amount
            }

            val cat = ExpenseCategory.fromString(exp.category)
            categorySums[cat] = (categorySums[cat] ?: 0.0) + amount
            categoryCounts[cat] = (categoryCounts[cat] ?: 0) + 1

            val author = exp.user_name.ifBlank { "Unknown" }
            partnerSums[author] = (partnerSums[author] ?: 0.0) + amount

            // Calculate Split & Net Balance
            val isMe = author.equals(myName, ignoreCase = true)
            when (exp.splitType) {
                SplitType.SHARED -> {
                    if (isMe) netDebt += (amount / 2.0) else netDebt -= (amount / 2.0)
                }
                SplitType.PAID_FOR_PARTNER -> {
                    if (isMe) netDebt += amount else netDebt -= amount
                }
                SplitType.SETTLEMENT -> {
                    // Settlement payment: if I paid, it decreases my owed amount (or pays back partner)
                    if (isMe) netDebt += amount else netDebt -= amount
                }
                SplitType.PERSONAL -> {
                    // Does not affect partner debt balance
                }
            }
        }

        val allTotal = if (monthTotal > 0.0) monthTotal else list.sumOf { it.amount }
        val categorySummaries = categorySums.map { (cat, sum) ->
            CategorySummary(
                category = cat,
                totalAmount = sum,
                count = categoryCounts[cat] ?: 0,
                percentage = if (allTotal > 0) ((sum / allTotal) * 100).toFloat() else 0f
            )
        }.sortedByDescending { it.totalAmount }

        val partnerSummaries = partnerSums.map { (name, sum) ->
            val total = list.sumOf { it.amount }
            PartnerSpendingSummary(
                partnerName = name,
                amount = sum,
                percentage = if (total > 0) ((sum / total) * 100).toFloat() else 0f
            )
        }.sortedByDescending { it.amount }

        // Budget & pacing calculation
        val monthlyBudget = session.monthlyBudget.coerceAtLeast(10000.0)
        val budgetRemaining = (monthlyBudget - monthTotal).coerceAtLeast(0.0)
        val budgetUsedPercent = ((monthTotal / monthlyBudget) * 100f).toFloat().coerceIn(0f, 150f)

        val cal = Calendar.getInstance()
        val totalDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val daysLeft = (totalDaysInMonth - currentDay + 1).coerceAtLeast(1)
        val dailyPace = budgetRemaining / daysLeft

        // Net balance summary text
        val balanceText = when {
            kotlin.math.abs(netDebt) < 1.0 -> "All settled up! 🎉"
            netDebt > 0 -> "Partner owes you ${FormatUtils.formatMmk(netDebt)}"
            else -> "You owe partner ${FormatUtils.formatMmk(-netDebt)}"
        }

        return DashboardSummary(
            todayTotal = todayTotal,
            monthTotal = monthTotal,
            totalCount = list.size,
            monthlyBudget = monthlyBudget,
            budgetRemaining = budgetRemaining,
            budgetUsedPercent = budgetUsedPercent,
            dailyPaceRemaining = dailyPace,
            daysLeftInMonth = daysLeft,
            netBalance = netDebt,
            balanceSummaryText = balanceText,
            categories = categorySummaries,
            partnerBreakdown = partnerSummaries
        )
    }

    private fun defaultSavingsGoals(): List<SavingsGoal> = emptyList()

    private fun defaultRecurringBills(): List<RecurringBill> = emptyList()

    // --- Live Location Sharing & History Actions ---

    private var locationTrackingJob: kotlinx.coroutines.Job? = null
    private var liveGpsStreamJob: kotlinx.coroutines.Job? = null

    private fun startLocationTrackingLoop() {
        locationTrackingJob?.cancel()
        locationTrackingJob = viewModelScope.launch {
            try {
                while (isActive) {
                    val session = userSession.value
                    if (session.isLocationSharingEnabled && session.coupleId.isNotBlank()) {
                        runCatching { broadcastCurrentLocation() }
                    }
                    delay(20_000)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("CoupleExpenseViewModel", "Location tracking loop warning: ${e.message}")
            }
        }
    }

    /**
     * Automatically enables live background location sharing once permission is granted
     * unless the user explicitly paused it previously, and keeps live GPS stream active.
     */
    fun onLocationPermissionAvailable() {
        val app = getApplication<Application>()
        if (!PermissionHelper.hasLocationPermission(app)) return
        viewModelScope.launch {
            try {
                val session = userSession.value
                if (session.isPaired && session.coupleId.isNotBlank()) {
                    if (!session.hasExplicitlySetLocationSharing && !session.isLocationSharingEnabled) {
                        preferences.setLocationSharing(true, userExplicit = false)
                    }
                    if (preferences.userSessionFlow.first().isLocationSharingEnabled) {
                        LocationTrackingService.start(app)
                        startLocationTrackingLoop()
                    }
                }
                refreshCurrentUserCoordinates(highAccuracy = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("CoupleExpenseViewModel", "Location permission activation warning: ${e.message}")
            }
        }
        startForegroundGpsStreamIfNeeded()
    }

    fun startForegroundGpsStreamIfNeeded() {
        val app = getApplication<Application>()
        if (!PermissionHelper.hasLocationPermission(app)) return
        if (liveGpsStreamJob?.isActive == true) return
        liveGpsStreamJob = viewModelScope.launch {
            try {
                locationHelper.locationUpdatesFlow(
                    intervalMs = 8_000L,
                    minDistanceMeters = 8f,
                    highAccuracy = true
                ).collect { (lat, lng) ->
                    _currentUserCoordinates.value = lat to lng
                    val session = userSession.value
                    if (session.isLocationSharingEnabled && session.isPaired) {
                        runCatching { publishCoordinates(lat, lng) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("CoupleExpenseViewModel", "Foreground GPS stream warning: ${e.message}")
            }
        }
    }

    suspend fun refreshCurrentUserCoordinates(highAccuracy: Boolean = false): Pair<Double, Double>? {
        val coords = locationHelper.getCurrentCoordinates(highAccuracy = highAccuracy)
        if (coords != null) {
            _currentUserCoordinates.value = coords
        }
        return coords
    }

    fun setLocationSharing(enabled: Boolean) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            preferences.setLocationSharing(enabled, userExplicit = true)
            val session = userSession.value
            if (enabled) {
                if (PermissionHelper.hasLocationPermission(app)) {
                    LocationTrackingService.start(app)
                    startLocationTrackingLoop()
                    startForegroundGpsStreamIfNeeded()
                    broadcastCurrentLocation(highAccuracy = true)
                }
                showMessage("Live background location sharing active ❤️")
            } else {
                locationTrackingJob?.cancel()
                locationTrackingJob = null
                LocationTrackingService.stop(app)
                if (session.coupleId.isNotBlank() && session.userName.isNotBlank()) {
                    val current = myLiveLocation.value
                    if (current != null) {
                        repository.updateLiveLocation(current.copy(is_sharing = false))
                    }
                }
                showMessage("Live location sharing paused")
            }
        }
    }

    suspend fun broadcastCurrentLocation(highAccuracy: Boolean = false): Pair<Double, Double>? {
        val session = userSession.value
        if (session.coupleId.isBlank() || session.userName.isBlank()) return null
        val coords = locationHelper.getCurrentCoordinates(highAccuracy = highAccuracy) ?: return null
        _currentUserCoordinates.value = coords
        publishCoordinates(coords.first, coords.second)
        return coords
    }

    suspend fun publishCoordinates(lat: Double, lng: Double) {
        val session = userSession.value
        if (session.coupleId.isBlank() || session.userName.isBlank()) return
        val nowMs = System.currentTimeMillis()
        val battery = locationHelper.getBatteryLevel()
        val timeSdf = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
        val dateSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val now = Date(nowMs)
        val timeStr = timeSdf.format(now)
        val dateStr = dateSdf.format(now)
        val placeName = locationHelper.getPlaceName(lat, lng)

        val liveLoc = LiveLocation(
            id = "loc-${session.coupleId}-${session.userName.lowercase(Locale.ROOT)}",
            couple_id = session.coupleId,
            user_name = session.userName,
            latitude = lat,
            longitude = lng,
            is_sharing = true,
            battery_level = battery,
            place_name = placeName,
            timestamp = timeStr,
            updated_at = nowMs
        )
        repository.updateLiveLocation(liveLoc)

        // Record history point if moved >= 35m OR stayed >= 2 minutes at current point (tracks exact dwell/stay)
        val lastHistory = locationHistory.value.firstOrNull { it.user_name.equals(session.userName, ignoreCase = true) }
        val movedMeters = if (lastHistory != null) {
            LocationHistoryAnalyzer.haversineMeters(lastHistory.latitude, lastHistory.longitude, lat, lng)
        } else {
            Double.MAX_VALUE
        }
        val elapsedMs = if (lastHistory != null) nowMs - lastHistory.recorded_at else Long.MAX_VALUE

        if (lastHistory == null || movedMeters >= 35.0 || elapsedMs >= 2 * 60 * 1000L) {
            val historyItem = LocationHistoryItem(
                id = UUID.randomUUID().toString(),
                couple_id = session.coupleId,
                user_name = session.userName,
                latitude = lat,
                longitude = lng,
                place_name = placeName,
                timestamp = timeStr,
                date = dateStr,
                recorded_at = nowMs
            )
            repository.recordLocationHistory(historyItem)
        }
    }

    fun recordManualLocationCheckpoint(note: String = "") {
        viewModelScope.launch {
            val session = userSession.value
            if (session.coupleId.isBlank() || session.userName.isBlank()) {
                showMessage("Please pair with your partner first")
                return@launch
            }
            val coords = locationHelper.getCurrentCoordinates(highAccuracy = true)
            if (coords != null) {
                _currentUserCoordinates.value = coords
                val nowMs = System.currentTimeMillis()
                val timeSdf = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
                val dateSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val now = Date(nowMs)
                val placeName = if (note.isNotBlank()) note else locationHelper.getPlaceName(coords.first, coords.second)

                val historyItem = LocationHistoryItem(
                    id = UUID.randomUUID().toString(),
                    couple_id = session.coupleId,
                    user_name = session.userName,
                    latitude = coords.first,
                    longitude = coords.second,
                    place_name = placeName,
                    timestamp = timeSdf.format(now),
                    date = dateSdf.format(now),
                    recorded_at = nowMs
                )
                repository.recordLocationHistory(historyItem)
                showMessage("Pinned '$placeName' at ${timeSdf.format(now)} 📍")
            } else {
                showMessage("Could not fetch current GPS location. Please check permissions.")
            }
        }
    }

    fun clearLocationHistory() {
        viewModelScope.launch {
            val coupleId = userSession.value.coupleId
            if (coupleId.isNotBlank()) {
                repository.clearLocationHistory(coupleId)
                showMessage("Location history cleared")
            }
        }
    }

    fun markPermissionsPrompted() {
        viewModelScope.launch {
            preferences.setPermissionsPrompted(true)
        }
    }
}
