package com.example.data.supabase

import android.util.Log
import com.example.data.local.LocalCoupleCache
import com.example.data.model.AccountLinkCode
import com.example.data.model.AuthResult
import com.example.data.model.Couple
import com.example.data.model.CoupleMemo
import com.example.data.model.CoupleSyncCounts
import com.example.data.model.DeviceSession
import com.example.data.model.Expense
import com.example.data.model.LinkCodeResult
import com.example.data.model.LiveLocation
import com.example.data.model.LocationHistoryItem
import com.example.data.model.MoneyAccount
import com.example.data.model.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class SupabaseRepository(
    private val clientManager: SupabaseClientManager,
    private val localCache: LocalCoupleCache? = null
) {
    private val scopeErrorHandler = CoroutineExceptionHandler { _, throwable ->
        Log.w("SupabaseRepository", "Background repository error: ${throwable.message}")
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + scopeErrorHandler)
    private val _expensesFlow = MutableStateFlow<List<Expense>>(emptyList())
    val expensesFlow: Flow<List<Expense>> = _expensesFlow.asStateFlow()

    private val _profilesFlow = MutableStateFlow<List<UserProfile>>(emptyList())
    val profilesFlow: Flow<List<UserProfile>> = _profilesFlow.asStateFlow()

    private val _memosFlow = MutableStateFlow<List<CoupleMemo>>(emptyList())
    val memosFlow: Flow<List<CoupleMemo>> = _memosFlow.asStateFlow()

    private val _liveLocationsFlow = MutableStateFlow<List<LiveLocation>>(emptyList())
    val liveLocationsFlow: Flow<List<LiveLocation>> = _liveLocationsFlow.asStateFlow()

    private val _locationHistoryFlow = MutableStateFlow<List<LocationHistoryItem>>(emptyList())
    val locationHistoryFlow: Flow<List<LocationHistoryItem>> = _locationHistoryFlow.asStateFlow()

    private val _deviceSessionsFlow = MutableStateFlow<List<DeviceSession>>(emptyList())
    val deviceSessionsFlow: Flow<List<DeviceSession>> = _deviceSessionsFlow.asStateFlow()

    private val _moneyAccountsFlow = MutableStateFlow<List<MoneyAccount>>(emptyList())
    val moneyAccountsFlow: Flow<List<MoneyAccount>> = _moneyAccountsFlow.asStateFlow()

    private var activeCoupleId: String = ""
    private var isRealtimeSessionActive: Boolean = false

    // Thread-safe in-memory local cache synced with persistent LocalCoupleCache (starts clean with zero dummy data)
    private val localCouples = ConcurrentHashMap<String, Couple>()
    private val localProfiles = CopyOnWriteArrayList<UserProfile>()
    private val localMemos = CopyOnWriteArrayList<CoupleMemo>()
    private val localLiveLocations = CopyOnWriteArrayList<LiveLocation>()
    private val localLocationHistory = CopyOnWriteArrayList<LocationHistoryItem>()
    private val localDeviceSessions = CopyOnWriteArrayList<DeviceSession>()
    private val localMoneyAccounts = CopyOnWriteArrayList<MoneyAccount>()
    private val localLinkCodes = ConcurrentHashMap<String, AccountLinkCode>() // link_code -> AccountLinkCode
    private val localExpenses = CopyOnWriteArrayList<Expense>()

    init {
        // Route Realtime events to ONLY the affected table, and polls to lightweight count-check sync
        clientManager.realtimeExpenseEvents.onEach { event ->
            val cid = activeCoupleId
            if (cid.isBlank()) return@onEach

            try {
                when {
                    event == "POLL_SYNC" -> syncChangedTablesOnly(cid)
                    event.startsWith("ACCOUNTS_LINKED:") -> syncAllCoupleData(cid)
                    event.startsWith("EVENT:") -> {
                        when {
                            event.contains("\"table\":\"expenses\"") -> syncChangedTablesOnly(cid, targetTable = "expenses")
                            event.contains("\"table\":\"memos\"") -> syncChangedTablesOnly(cid, targetTable = "memos")
                            event.contains("\"table\":\"profiles\"") -> refreshProfiles(cid)
                            event.contains("\"table\":\"live_locations\"") -> refreshLiveLocations(cid)
                            event.contains("\"table\":\"location_history\"") -> syncChangedTablesOnly(cid, targetTable = "location_history")
                            event.contains("\"table\":\"device_sessions\"") -> refreshDeviceSessions(cid)
                            event.contains("\"table\":\"money_accounts\"") -> refreshMoneyAccounts(cid)
                            else -> syncChangedTablesOnly(cid)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("SupabaseRepository", "Realtime sync event warning: ${e.message}")
            }
        }.launchIn(scope)
    }

    fun initCoupleSession(coupleId: String, forceFullSync: Boolean = false) {
        if (coupleId.isBlank()) return
        val alreadyRunning = activeCoupleId == coupleId && isRealtimeSessionActive
        if (alreadyRunning && !forceFullSync) {
            return
        }
        activeCoupleId = coupleId
        isRealtimeSessionActive = true
        runCatching { clientManager.startRealtime(coupleId) }
        scope.launch {
            try {
                hydrateFromLocalCache(coupleId)
                flushPendingChanges(coupleId)
                if (forceFullSync) {
                    syncAllCoupleData(coupleId)
                } else {
                    syncChangedTablesOnly(coupleId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("SupabaseRepository", "Initial session sync warning: ${e.message}")
            }
        }
    }

    fun clearSessionState(
        coupleIdToClearCache: String = "",
        clearDiskCache: Boolean = false
    ) {
        val cid = coupleIdToClearCache.ifBlank { activeCoupleId }
        activeCoupleId = ""
        isRealtimeSessionActive = false
        runCatching { clientManager.stopRealtime() }
        _expensesFlow.value = emptyList()
        _profilesFlow.value = emptyList()
        _memosFlow.value = emptyList()
        _liveLocationsFlow.value = emptyList()
        _locationHistoryFlow.value = emptyList()
        _deviceSessionsFlow.value = emptyList()
        _moneyAccountsFlow.value = emptyList()

        // Clear active workspace RAM caches so logging out or switching accounts has zero cross-account bleed,
        // while keeping LocalCoupleCache JSON on disk unless clearDiskCache == true.
        localExpenses.clear()
        localProfiles.clear()
        localMemos.clear()
        localLiveLocations.clear()
        localLocationHistory.clear()
        localDeviceSessions.clear()
        localMoneyAccounts.clear()

        if (clearDiskCache && cid.isNotBlank()) {
            scope.launch {
                localCache?.clearCoupleCache(cid)
            }
        }
    }

    suspend fun getCachedItemCount(coupleId: String): Int {
        return localCache?.getCachedItemCount(coupleId) ?: (
            _expensesFlow.value.size +
                _memosFlow.value.size +
                _profilesFlow.value.size +
                _liveLocationsFlow.value.size +
                _locationHistoryFlow.value.size +
                _deviceSessionsFlow.value.size +
                _moneyAccountsFlow.value.size
            )
    }

    suspend fun hydrateFromLocalCache(coupleId: String) {
        if (coupleId.isBlank()) return
        val snapshot = localCache?.getSnapshot(coupleId) ?: return

        val hydratedExpenses = snapshot.expenses
            .filter { it.id !in snapshot.deleted_expense_ids }
            .distinctBy { it.id }
        if (hydratedExpenses.isNotEmpty()) {
            localExpenses.removeAll { it.couple_id == coupleId }
            localExpenses.addAll(hydratedExpenses)
            if (_expensesFlow.value != hydratedExpenses) {
                _expensesFlow.value = hydratedExpenses
            }
        }

        if (snapshot.profiles.isNotEmpty()) {
            localProfiles.removeAll { it.couple_id == coupleId }
            localProfiles.addAll(snapshot.profiles)
            if (_profilesFlow.value != snapshot.profiles) {
                _profilesFlow.value = snapshot.profiles
            }
        }

        val hydratedMemos = snapshot.memos
            .filter { it.id !in snapshot.deleted_memo_ids }
            .distinctBy { it.id }
        if (hydratedMemos.isNotEmpty()) {
            localMemos.removeAll { it.couple_id == coupleId }
            localMemos.addAll(hydratedMemos)
            if (_memosFlow.value != hydratedMemos) {
                _memosFlow.value = hydratedMemos
            }
        }

        if (snapshot.live_locations.isNotEmpty()) {
            localLiveLocations.removeAll { it.couple_id == coupleId }
            localLiveLocations.addAll(snapshot.live_locations)
            if (_liveLocationsFlow.value != snapshot.live_locations) {
                _liveLocationsFlow.value = snapshot.live_locations
            }
        }

        if (snapshot.location_history.isNotEmpty()) {
            localLocationHistory.removeAll { it.couple_id == coupleId }
            localLocationHistory.addAll(snapshot.location_history)
            if (_locationHistoryFlow.value != snapshot.location_history) {
                _locationHistoryFlow.value = snapshot.location_history
            }
        }

        if (snapshot.device_sessions.isNotEmpty()) {
            localDeviceSessions.removeAll { it.couple_id == coupleId }
            localDeviceSessions.addAll(snapshot.device_sessions)
            if (_deviceSessionsFlow.value != snapshot.device_sessions) {
                _deviceSessionsFlow.value = snapshot.device_sessions
            }
        }

        val hydratedMoneyAccounts = snapshot.money_accounts
            .filter { it.id !in snapshot.deleted_money_account_ids }
            .distinctBy { it.id }
            .sortedByDescending { it.updated_at }
        if (hydratedMoneyAccounts.isNotEmpty()) {
            localMoneyAccounts.removeAll { it.couple_id == coupleId }
            localMoneyAccounts.addAll(hydratedMoneyAccounts)
            if (_moneyAccountsFlow.value != hydratedMoneyAccounts) {
                _moneyAccountsFlow.value = hydratedMoneyAccounts
            }
        }
    }

    private suspend fun persistExpensesCache(coupleId: String, list: List<Expense>, latestOverride: String? = null) {
        localCache?.updateSnapshot(coupleId) { snap ->
            val latest = latestOverride ?: list.maxOfOrNull { it.created_at.orEmpty() } ?: ""
            snap.copy(
                expenses = list,
                counts = snap.counts.copy(
                    expenses_count = list.size,
                    expenses_latest = latest
                )
            )
        }
    }

    private suspend fun persistProfilesCache(coupleId: String, list: List<UserProfile>, latestOverride: String? = null) {
        localCache?.updateSnapshot(coupleId) { snap ->
            val latest = latestOverride ?: list.mapNotNull { it.updated_at }.maxOrNull() ?: ""
            snap.copy(
                profiles = list,
                counts = snap.counts.copy(
                    profiles_count = list.size,
                    profiles_latest = latest
                )
            )
        }
    }

    private suspend fun persistMemosCache(coupleId: String, list: List<CoupleMemo>, latestOverride: String? = null) {
        localCache?.updateSnapshot(coupleId) { snap ->
            val latest = latestOverride ?: list.maxOfOrNull { it.created_at } ?: ""
            snap.copy(
                memos = list,
                counts = snap.counts.copy(
                    memos_count = list.size,
                    memos_latest = latest
                )
            )
        }
    }

    private suspend fun persistLiveLocationsCache(coupleId: String, list: List<LiveLocation>, latestOverride: Long? = null) {
        localCache?.updateSnapshot(coupleId) { snap ->
            val latest = latestOverride ?: list.maxOfOrNull { it.updated_at } ?: 0L
            snap.copy(
                live_locations = list,
                counts = snap.counts.copy(
                    live_locations_count = list.size,
                    live_locations_latest = latest
                )
            )
        }
    }

    private suspend fun persistLocationHistoryCache(coupleId: String, list: List<LocationHistoryItem>, latestOverride: Long? = null) {
        localCache?.updateSnapshot(coupleId) { snap ->
            val latest = latestOverride ?: list.maxOfOrNull { it.recorded_at } ?: 0L
            snap.copy(
                location_history = list,
                counts = snap.counts.copy(
                    location_history_count = list.size,
                    location_history_latest = latest
                )
            )
        }
    }

    private suspend fun persistDeviceSessionsCache(coupleId: String, list: List<DeviceSession>, latestOverride: String? = null) {
        localCache?.updateSnapshot(coupleId) { snap ->
            val latest = latestOverride ?: list.maxOfOrNull { it.last_login_at } ?: ""
            snap.copy(
                device_sessions = list,
                counts = snap.counts.copy(
                    device_sessions_count = list.size,
                    device_sessions_latest = latest
                )
            )
        }
    }

    private suspend fun persistMoneyAccountsCache(coupleId: String, list: List<MoneyAccount>, latestOverride: Long? = null) {
        localCache?.updateSnapshot(coupleId) { snap ->
            val latest = latestOverride ?: list.maxOfOrNull { it.updated_at } ?: 0L
            snap.copy(
                money_accounts = list,
                counts = snap.counts.copy(
                    money_accounts_count = list.size,
                    money_accounts_latest = latest
                )
            )
        }
    }

    suspend fun refreshExpenses(coupleId: String, latestMarkerOverride: String? = null) {
        val snap = localCache?.getSnapshot(coupleId)
        val pendingIds = snap?.pending_expense_ids.orEmpty()
        val deletedIds = snap?.deleted_expense_ids.orEmpty()
        val cachedOrLocalPending = (localExpenses.filter { it.couple_id == coupleId } + snap?.expenses.orEmpty())
            .distinctBy { it.id }
            .filter { it.couple_id == coupleId && it.id in pendingIds && it.id !in deletedIds }

        val next = if (clientManager.isConfigured()) {
            val result = clientManager.getExpenses(coupleId)
            result.fold(
                onSuccess = { list ->
                    val filteredRemote = list.filter { it.id !in deletedIds }
                    val remoteIds = filteredRemote.map { it.id }.toSet()
                    val localOnly = cachedOrLocalPending.filter { it.id !in remoteIds }
                    (filteredRemote + localOnly).distinctBy { it.id }.sortedByDescending { it.created_at }
                },
                onFailure = {
                    val cached = snap?.expenses?.filter { it.id !in deletedIds }
                    if (!cached.isNullOrEmpty()) cached.distinctBy { it.id }
                    else localExpenses.filter { it.couple_id == coupleId && it.id !in deletedIds }
                        .distinctBy { it.id }
                        .sortedByDescending { it.created_at }
                }
            )
        } else {
            (localExpenses.filter { it.couple_id == coupleId } + snap?.expenses.orEmpty())
                .filter { it.id !in deletedIds }
                .distinctBy { it.id }
                .sortedByDescending { it.created_at }
        }
        if (_expensesFlow.value != next) {
            _expensesFlow.value = next
        }
        persistExpensesCache(coupleId, next, latestMarkerOverride)
    }

    suspend fun refreshProfiles(coupleId: String, latestMarkerOverride: String? = null) {
        val snap = localCache?.getSnapshot(coupleId)
        val next = if (clientManager.isConfigured()) {
            val result = clientManager.getProfiles(coupleId)
            result.fold(
                onSuccess = { list ->
                    val remoteIds = list.map { it.id }.toSet()
                    val localOnly = localProfiles.filter { it.couple_id == coupleId && it.id !in remoteIds }
                    (list + localOnly).distinctBy { it.id.ifBlank { it.user_name.lowercase() } }
                },
                onFailure = {
                    val cached = snap?.profiles
                    if (!cached.isNullOrEmpty()) cached.distinctBy { it.id.ifBlank { it.user_name.lowercase() } }
                    else localProfiles.filter { it.couple_id == coupleId }
                }
            )
        } else {
            (localProfiles.filter { it.couple_id == coupleId } + snap?.profiles.orEmpty())
                .distinctBy { it.id.ifBlank { it.user_name.lowercase() } }
        }
        if (_profilesFlow.value != next) {
            _profilesFlow.value = next
        }
        persistProfilesCache(coupleId, next, latestMarkerOverride)
    }

    suspend fun refreshMemos(coupleId: String, latestMarkerOverride: String? = null) {
        val snap = localCache?.getSnapshot(coupleId)
        val pendingIds = snap?.pending_memo_ids.orEmpty()
        val deletedIds = snap?.deleted_memo_ids.orEmpty()
        val cachedOrLocalPending = (localMemos.filter { it.couple_id == coupleId } + snap?.memos.orEmpty())
            .distinctBy { it.id }
            .filter { it.couple_id == coupleId && it.id in pendingIds && it.id !in deletedIds }

        val next = if (clientManager.isConfigured()) {
            val result = clientManager.getMemos(coupleId)
            result.fold(
                onSuccess = { list ->
                    val filteredRemote = list.filter { it.id !in deletedIds }
                    val remoteIds = filteredRemote.map { it.id }.toSet()
                    val localOnly = cachedOrLocalPending.filter { it.id !in remoteIds }
                    (filteredRemote + localOnly).distinctBy { it.id }.sortedByDescending { it.created_at }
                },
                onFailure = {
                    val cached = snap?.memos?.filter { it.id !in deletedIds }
                    if (!cached.isNullOrEmpty()) cached.distinctBy { it.id }
                    else localMemos.filter { it.couple_id == coupleId && it.id !in deletedIds }
                        .distinctBy { it.id }
                        .sortedByDescending { it.created_at }
                }
            )
        } else {
            (localMemos.filter { it.couple_id == coupleId } + snap?.memos.orEmpty())
                .filter { it.id !in deletedIds }
                .distinctBy { it.id }
                .sortedByDescending { it.created_at }
        }
        if (_memosFlow.value != next) {
            _memosFlow.value = next
        }
        persistMemosCache(coupleId, next, latestMarkerOverride)
    }

    suspend fun createCouple(codeOverride: String? = null): Result<Couple> {
        val code = codeOverride ?: generateCoupleCode()
        return if (clientManager.isConfigured()) {
            val remoteResult = clientManager.createCouple(code)
            if (remoteResult.isSuccess) {
                val couple = remoteResult.getOrThrow()
                localCouples[couple.couple_code] = couple
                remoteResult
            } else {
                val fallbackCouple = Couple(
                    id = UUID.randomUUID().toString(),
                    couple_code = code,
                    created_at = getTodayIso()
                )
                localCouples[code] = fallbackCouple
                Log.w("SupabaseRepository", "Supabase createCouple failed (${remoteResult.exceptionOrNull()?.message}). Proceeding with resilient local couple session.")
                Result.success(fallbackCouple)
            }
        } else {
            val couple = Couple(
                id = UUID.randomUUID().toString(),
                couple_code = code,
                created_at = getTodayIso()
            )
            localCouples[code] = couple
            Result.success(couple)
        }
    }

    suspend fun joinCouple(code: String): Result<Couple> {
        val cleanCode = code.trim().uppercase()
        if (cleanCode.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter your partner's couple code."))
        }
        return if (clientManager.isConfigured()) {
            val res = clientManager.getCoupleByCode(cleanCode)
            if (res.isSuccess) {
                val remoteCouple = res.getOrThrow()
                if (remoteCouple != null) {
                    localCouples[cleanCode] = remoteCouple
                    Result.success(remoteCouple)
                } else {
                    val local = localCouples[cleanCode]
                    if (local != null) {
                        Result.success(local)
                    } else {
                        Result.failure(NoSuchElementException("Couple code '$cleanCode' not found. Please double-check with your partner."))
                    }
                }
            } else {
                val local = localCouples[cleanCode]
                if (local != null) {
                    Result.success(local)
                } else {
                    Result.failure(
                        res.exceptionOrNull()
                            ?: NoSuchElementException("Unable to verify couple code '$cleanCode'. Please check your connection.")
                    )
                }
            }
        } else {
            val found = localCouples[cleanCode]
            if (found != null) {
                Result.success(found)
            } else {
                Result.failure(NoSuchElementException("Couple code '$cleanCode' not found. Please double-check with your partner."))
            }
        }
    }

    suspend fun addExpense(expense: Expense): Result<Expense> {
        var syncedRemotely = false
        val saved = if (clientManager.isConfigured()) {
            val result = clientManager.insertExpense(expense)
            if (result.isSuccess) {
                syncedRemotely = true
                result.getOrThrow()
            } else {
                Log.w("SupabaseRepository", "Remote insertExpense failed (${result.exceptionOrNull()?.message}), queuing locally")
                expense
            }
        } else {
            expense
        }
        localExpenses.removeAll { it.id == saved.id }
        localExpenses.add(0, saved)
        val next = (listOf(saved) + _expensesFlow.value.filterNot { it.id == saved.id })
            .distinctBy { it.id }
            .sortedByDescending { it.created_at }
        if (_expensesFlow.value != next) _expensesFlow.value = next
        localCache?.updateSnapshot(expense.couple_id) { snap ->
            val nextPending = if (syncedRemotely) {
                snap.pending_expense_ids - saved.id
            } else {
                snap.pending_expense_ids + saved.id
            }
            snap.copy(
                expenses = next,
                counts = snap.counts.copy(
                    expenses_count = next.size,
                    expenses_latest = next.maxOfOrNull { it.created_at.orEmpty() } ?: ""
                ),
                pending_expense_ids = nextPending,
                deleted_expense_ids = snap.deleted_expense_ids - saved.id
            )
        }
        return Result.success(saved)
    }

    suspend fun deleteExpense(expenseId: String, coupleId: String): Result<Boolean> {
        localExpenses.removeAll { it.id == expenseId }
        val deletedRemotely = if (clientManager.isConfigured()) {
            clientManager.deleteExpense(expenseId).isSuccess
        } else {
            false
        }
        val next = _expensesFlow.value.filterNot { it.id == expenseId }
        if (_expensesFlow.value != next) _expensesFlow.value = next
        if (coupleId.isNotBlank()) {
            localCache?.updateSnapshot(coupleId) { snap ->
                snap.copy(
                    expenses = next,
                    counts = snap.counts.copy(
                        expenses_count = next.size,
                        expenses_latest = next.maxOfOrNull { it.created_at.orEmpty() } ?: ""
                    ),
                    pending_expense_ids = snap.pending_expense_ids - expenseId,
                    deleted_expense_ids = if (deletedRemotely) {
                        snap.deleted_expense_ids - expenseId
                    } else {
                        snap.deleted_expense_ids + expenseId
                    }
                )
            }
        }
        return Result.success(true)
    }

    suspend fun saveProfile(profile: UserProfile): Result<UserProfile> {
        localProfiles.removeAll { it.couple_id == profile.couple_id && (it.id == profile.id || it.user_name.equals(profile.user_name, ignoreCase = true)) }
        localProfiles.add(profile)
        val saved = if (clientManager.isConfigured()) {
            val res = clientManager.upsertProfile(profile)
            if (res.isSuccess) res.getOrThrow()
            else {
                Log.w("SupabaseRepository", "Remote upsertProfile failed (${res.exceptionOrNull()?.message}), cached locally")
                profile
            }
        } else {
            profile
        }
        val next = (_profilesFlow.value.filterNot {
            it.couple_id == saved.couple_id && (it.id == saved.id || it.user_name.equals(saved.user_name, ignoreCase = true))
        } + saved)
        if (_profilesFlow.value != next) _profilesFlow.value = next
        persistProfilesCache(profile.couple_id, next)
        return Result.success(saved)
    }

    suspend fun addMemo(memo: CoupleMemo): Result<CoupleMemo> {
        var syncedRemotely = false
        val saved = if (clientManager.isConfigured()) {
            val result = clientManager.insertMemo(memo)
            if (result.isSuccess) {
                syncedRemotely = true
                result.getOrThrow()
            } else {
                Log.w("SupabaseRepository", "Remote insertMemo failed (${result.exceptionOrNull()?.message}), queuing locally")
                memo
            }
        } else {
            memo
        }
        localMemos.removeAll { it.id == saved.id }
        localMemos.add(0, saved)
        val next = (listOf(saved) + _memosFlow.value.filterNot { it.id == saved.id })
            .distinctBy { it.id }
            .sortedByDescending { it.created_at }
        if (_memosFlow.value != next) _memosFlow.value = next
        localCache?.updateSnapshot(memo.couple_id) { snap ->
            val nextPending = if (syncedRemotely) {
                snap.pending_memo_ids - saved.id
            } else {
                snap.pending_memo_ids + saved.id
            }
            snap.copy(
                memos = next,
                counts = snap.counts.copy(
                    memos_count = next.size,
                    memos_latest = next.maxOfOrNull { it.created_at } ?: ""
                ),
                pending_memo_ids = nextPending,
                deleted_memo_ids = snap.deleted_memo_ids - saved.id
            )
        }
        return Result.success(saved)
    }

    suspend fun deleteMemo(memoId: String, coupleId: String): Result<Boolean> {
        localMemos.removeAll { it.id == memoId }
        val deletedRemotely = if (clientManager.isConfigured()) {
            clientManager.deleteMemo(memoId).isSuccess
        } else {
            false
        }
        val next = _memosFlow.value.filterNot { it.id == memoId }
        if (_memosFlow.value != next) _memosFlow.value = next
        if (coupleId.isNotBlank()) {
            localCache?.updateSnapshot(coupleId) { snap ->
                snap.copy(
                    memos = next,
                    counts = snap.counts.copy(
                        memos_count = next.size,
                        memos_latest = next.maxOfOrNull { it.created_at } ?: ""
                    ),
                    pending_memo_ids = snap.pending_memo_ids - memoId,
                    deleted_memo_ids = if (deletedRemotely) {
                        snap.deleted_memo_ids - memoId
                    } else {
                        snap.deleted_memo_ids + memoId
                    }
                )
            }
        }
        return Result.success(true)
    }

    // --- Live Location & History Operations ---

    suspend fun refreshLiveLocations(coupleId: String, latestOverride: Long? = null) {
        val next = if (clientManager.isConfigured()) {
            val result = clientManager.getLiveLocations(coupleId)
            result.fold(
                onSuccess = { list ->
                    val remoteUserNames = list.map { it.user_name.lowercase() }.toSet()
                    val localOnly = localLiveLocations.filter { it.couple_id == coupleId && it.user_name.lowercase() !in remoteUserNames }
                    list + localOnly
                },
                onFailure = {
                    val cached = localCache?.getSnapshot(coupleId)?.live_locations
                    if (!cached.isNullOrEmpty()) cached
                    else localLiveLocations.filter { it.couple_id == coupleId }
                }
            )
        } else {
            localLiveLocations.filter { it.couple_id == coupleId }
        }
        if (_liveLocationsFlow.value != next) {
            _liveLocationsFlow.value = next
        }
        persistLiveLocationsCache(coupleId, next, latestOverride)
    }

    suspend fun refreshLocationHistory(coupleId: String, latestOverride: Long? = null) {
        val next = if (clientManager.isConfigured()) {
            val result = clientManager.getLocationHistory(coupleId)
            result.fold(
                onSuccess = { list ->
                    val remoteIds = list.map { it.id }.toSet()
                    val localOnly = localLocationHistory.filter { it.couple_id == coupleId && it.id !in remoteIds }
                    (list + localOnly).sortedByDescending { it.recorded_at }
                },
                onFailure = {
                    val cached = localCache?.getSnapshot(coupleId)?.location_history
                    if (!cached.isNullOrEmpty()) cached
                    else localLocationHistory.filter { it.couple_id == coupleId }.sortedByDescending { it.recorded_at }
                }
            )
        } else {
            localLocationHistory.filter { it.couple_id == coupleId }.sortedByDescending { it.recorded_at }
        }
        if (_locationHistoryFlow.value != next) {
            _locationHistoryFlow.value = next
        }
        persistLocationHistoryCache(coupleId, next, latestOverride)
    }

    suspend fun updateLiveLocation(location: LiveLocation): Result<LiveLocation> {
        localLiveLocations.removeAll { it.couple_id == location.couple_id && it.user_name.equals(location.user_name, ignoreCase = true) }
        localLiveLocations.add(location)
        val saved = if (clientManager.isConfigured()) {
            val res = clientManager.upsertLiveLocation(location)
            if (res.isSuccess) res.getOrThrow() else location
        } else {
            location
        }
        val next = (_liveLocationsFlow.value.filterNot {
            it.couple_id == saved.couple_id && it.user_name.equals(saved.user_name, ignoreCase = true)
        } + saved)
        if (_liveLocationsFlow.value != next) _liveLocationsFlow.value = next
        persistLiveLocationsCache(location.couple_id, next)
        return Result.success(saved)
    }

    suspend fun recordLocationHistory(historyItem: LocationHistoryItem): Result<LocationHistoryItem> {
        val itemWithId = if (historyItem.id.isBlank()) historyItem.copy(id = UUID.randomUUID().toString()) else historyItem
        localLocationHistory.removeAll { it.id == itemWithId.id }
        localLocationHistory.add(0, itemWithId)
        val saved = if (clientManager.isConfigured()) {
            val res = clientManager.insertLocationHistory(itemWithId)
            if (res.isSuccess) res.getOrThrow() else itemWithId
        } else {
            itemWithId
        }
        val next = (listOf(saved) + _locationHistoryFlow.value.filterNot { it.id == saved.id })
            .sortedByDescending { it.recorded_at }
        if (_locationHistoryFlow.value != next) _locationHistoryFlow.value = next
        persistLocationHistoryCache(itemWithId.couple_id, next)
        return Result.success(saved)
    }

    suspend fun clearLocationHistory(coupleId: String): Result<Boolean> {
        localLocationHistory.removeAll { it.couple_id == coupleId }
        val res = if (clientManager.isConfigured()) {
            clientManager.clearLocationHistory(coupleId)
        } else {
            Result.success(true)
        }
        if (_locationHistoryFlow.value.isNotEmpty()) _locationHistoryFlow.value = emptyList()
        persistLocationHistoryCache(coupleId, emptyList(), 0L)
        return res
    }

    // --- Device Login Session Operations ---

    suspend fun refreshDeviceSessions(coupleId: String, latestOverride: String? = null) {
        val next = if (clientManager.isConfigured()) {
            val result = clientManager.getDeviceSessionsByCouple(coupleId)
            result.fold(
                onSuccess = { list ->
                    val remoteDeviceIds = list.map { it.device_id }.toSet()
                    val localOnly = localDeviceSessions.filter { it.couple_id == coupleId && it.is_active && it.device_id !in remoteDeviceIds }
                    (list + localOnly).sortedByDescending { it.last_login_at }
                },
                onFailure = {
                    val cached = localCache?.getSnapshot(coupleId)?.device_sessions
                    if (!cached.isNullOrEmpty()) cached
                    else localDeviceSessions.filter { it.couple_id == coupleId && it.is_active }.sortedByDescending { it.last_login_at }
                }
            )
        } else {
            localDeviceSessions.filter { it.couple_id == coupleId && it.is_active }.sortedByDescending { it.last_login_at }
        }
        if (_deviceSessionsFlow.value != next) {
            _deviceSessionsFlow.value = next
        }
        persistDeviceSessionsCache(coupleId, next, latestOverride)
    }

    suspend fun saveDeviceSession(deviceSession: DeviceSession): Result<DeviceSession> {
        localDeviceSessions.removeAll { it.device_id == deviceSession.device_id }
        localDeviceSessions.add(0, deviceSession)
        val saved = if (clientManager.isConfigured()) {
            val res = clientManager.upsertDeviceSession(deviceSession)
            if (res.isSuccess) res.getOrThrow() else deviceSession
        } else {
            deviceSession
        }
        val next = (listOf(saved) + _deviceSessionsFlow.value.filterNot { it.device_id == saved.device_id })
            .sortedByDescending { it.last_login_at }
        if (_deviceSessionsFlow.value != next) _deviceSessionsFlow.value = next
        persistDeviceSessionsCache(deviceSession.couple_id, next)
        return Result.success(saved)
    }

    suspend fun restoreDeviceSession(deviceId: String): DeviceSession? {
        if (deviceId.isBlank()) return null
        if (clientManager.isConfigured()) {
            val res = clientManager.getDeviceSessionByDeviceId(deviceId)
            val remote = res.getOrNull()
            if (remote != null && remote.is_active) {
                localDeviceSessions.removeAll { it.device_id == remote.device_id }
                localDeviceSessions.add(0, remote)
                return remote
            }
        }
        return localDeviceSessions.firstOrNull { it.device_id == deviceId && it.is_active }
    }

    suspend fun removeDeviceSession(deviceId: String, coupleId: String): Result<Boolean> {
        localDeviceSessions.removeAll { it.device_id == deviceId }
        val res = if (clientManager.isConfigured()) {
            clientManager.removeDeviceSession(deviceId)
        } else {
            Result.success(true)
        }
        if (coupleId.isNotBlank()) {
            val next = _deviceSessionsFlow.value.filterNot { it.device_id == deviceId }
            if (_deviceSessionsFlow.value != next) _deviceSessionsFlow.value = next
            persistDeviceSessionsCache(coupleId, next)
        }
        return res
    }

    // --- Separated Per-Person Money Accounts (Cash / Multi-Bank / Card) Operations ---

    suspend fun refreshMoneyAccounts(coupleId: String, latestOverride: Long? = null) {
        val snap = localCache?.getSnapshot(coupleId)
        val pendingIds = snap?.pending_money_account_ids.orEmpty()
        val deletedIds = snap?.deleted_money_account_ids.orEmpty()
        val localAndCached = (localMoneyAccounts.filter { it.couple_id == coupleId } + snap?.money_accounts.orEmpty())
            .distinctBy { it.id }
            .filter { it.couple_id == coupleId && it.id !in deletedIds }
        val pendingLocal = localAndCached.filter { it.id in pendingIds }

        val next = if (clientManager.isConfigured()) {
            val result = clientManager.getMoneyAccounts(coupleId)
            result.fold(
                onSuccess = { list ->
                    val filteredRemote = list.filter { it.id !in deletedIds }
                    val pendingById = pendingLocal.associateBy { it.id }
                    val mergedRemote = filteredRemote.map { remoteAcc ->
                        val pendingAcc = pendingById[remoteAcc.id]
                        if (pendingAcc != null && pendingAcc.updated_at >= remoteAcc.updated_at) pendingAcc else remoteAcc
                    }
                    val remoteIds = mergedRemote.map { it.id }.toSet()
                    val unsyncedOnly = pendingLocal.filter { it.id !in remoteIds }
                    (mergedRemote + unsyncedOnly).distinctBy { it.id }.sortedByDescending { it.updated_at }
                },
                onFailure = {
                    val cached = snap?.money_accounts?.filter { it.id !in deletedIds }
                    if (!cached.isNullOrEmpty()) cached.distinctBy { it.id }.sortedByDescending { it.updated_at }
                    else localMoneyAccounts.filter { it.couple_id == coupleId && it.id !in deletedIds }
                        .distinctBy { it.id }
                        .sortedByDescending { it.updated_at }
                }
            )
        } else {
            localAndCached.sortedByDescending { it.updated_at }
        }
        localMoneyAccounts.removeAll { it.couple_id == coupleId }
        localMoneyAccounts.addAll(next)
        if (_moneyAccountsFlow.value != next) {
            _moneyAccountsFlow.value = next
        }
        persistMoneyAccountsCache(coupleId, next, latestOverride)
    }

    suspend fun upsertMoneyAccount(account: MoneyAccount): Result<MoneyAccount> {
        val normalized = account.copy(
            id = account.id.ifBlank { UUID.randomUUID().toString() },
            updated_at = System.currentTimeMillis()
        )
        var syncedRemotely = false
        val saved = if (clientManager.isConfigured()) {
            val res = clientManager.upsertMoneyAccount(normalized)
            if (res.isSuccess) {
                syncedRemotely = true
                res.getOrThrow()
            } else {
                Log.w("SupabaseRepository", "Remote upsertMoneyAccount failed (${res.exceptionOrNull()?.message}), queued locally")
                normalized
            }
        } else {
            normalized
        }
        localMoneyAccounts.removeAll { it.id == saved.id }
        localMoneyAccounts.add(0, saved)
        val next = (listOf(saved) + _moneyAccountsFlow.value.filterNot { it.id == saved.id })
            .distinctBy { it.id }
            .sortedByDescending { it.updated_at }
        if (_moneyAccountsFlow.value != next) _moneyAccountsFlow.value = next

        localCache?.updateSnapshot(saved.couple_id) { snap ->
            val nextPending = if (syncedRemotely) {
                snap.pending_money_account_ids - saved.id
            } else {
                snap.pending_money_account_ids + saved.id
            }
            snap.copy(
                money_accounts = next,
                counts = snap.counts.copy(
                    money_accounts_count = next.size,
                    money_accounts_latest = next.maxOfOrNull { it.updated_at } ?: 0L
                ),
                pending_money_account_ids = nextPending,
                deleted_money_account_ids = snap.deleted_money_account_ids - saved.id
            )
        }
        return Result.success(saved)
    }

    suspend fun deleteMoneyAccount(accountId: String, coupleId: String): Result<Boolean> {
        localMoneyAccounts.removeAll { it.id == accountId }
        val deletedRemotely = if (clientManager.isConfigured()) {
            clientManager.deleteMoneyAccount(accountId).isSuccess
        } else {
            false
        }
        val next = _moneyAccountsFlow.value.filterNot { it.id == accountId }
        if (_moneyAccountsFlow.value != next) _moneyAccountsFlow.value = next
        if (coupleId.isNotBlank()) {
            localCache?.updateSnapshot(coupleId) { snap ->
                snap.copy(
                    money_accounts = next,
                    counts = snap.counts.copy(
                        money_accounts_count = next.size,
                        money_accounts_latest = next.maxOfOrNull { it.updated_at } ?: 0L
                    ),
                    pending_money_account_ids = snap.pending_money_account_ids - accountId,
                    deleted_money_account_ids = if (deletedRemotely) {
                        snap.deleted_money_account_ids - accountId
                    } else {
                        snap.deleted_money_account_ids + accountId
                    }
                )
            }
        }
        return Result.success(true)
    }

    /**
     * Adjusts an account's real current balance by [deltaAmount] (negative for expenses, positive for deposits/refunds)
     * and persists the synced value to both local cache and Supabase.
     */
    suspend fun adjustMoneyAccountBalance(
        accountId: String,
        coupleId: String,
        deltaAmount: Double
    ): Result<MoneyAccount?> {
        if (accountId.isBlank() || deltaAmount == 0.0) return Result.success(null)
        val target = _moneyAccountsFlow.value.firstOrNull { it.id == accountId }
            ?: localMoneyAccounts.firstOrNull { it.id == accountId }
            ?: localCache?.getSnapshot(coupleId)?.money_accounts?.firstOrNull { it.id == accountId }
            ?: return Result.success(null)

        val updated = target.copy(
            balance = target.balance + deltaAmount,
            updated_at = System.currentTimeMillis()
        )
        return upsertMoneyAccount(updated).map { it }
    }

    suspend fun transferBetweenMoneyAccounts(
        fromAccountId: String,
        toAccountId: String,
        amount: Double,
        coupleId: String
    ): Result<Pair<MoneyAccount, MoneyAccount>> {
        if (amount <= 0.0) {
            return Result.failure(IllegalArgumentException("Transfer amount must be greater than zero"))
        }
        if (fromAccountId == toAccountId) {
            return Result.failure(IllegalArgumentException("Select two different accounts to transfer"))
        }
        val currentList = _moneyAccountsFlow.value.ifEmpty {
            localMoneyAccounts.filter { it.couple_id == coupleId }
        }
        val fromAcc = currentList.firstOrNull { it.id == fromAccountId }
            ?: return Result.failure(IllegalArgumentException("Source account not found"))
        val toAcc = currentList.firstOrNull { it.id == toAccountId }
            ?: return Result.failure(IllegalArgumentException("Destination account not found"))

        val now = System.currentTimeMillis()
        val updatedFrom = fromAcc.copy(balance = fromAcc.balance - amount, updated_at = now)
        val updatedTo = toAcc.copy(balance = toAcc.balance + amount, updated_at = now + 1)

        upsertMoneyAccount(updatedFrom)
        upsertMoneyAccount(updatedTo)
        return Result.success(updatedFrom to updatedTo)
    }

    suspend fun testSupabaseConnection(): Result<String> = clientManager.testConnection()

    fun getActiveSupabaseHost(): String = clientManager.getActiveHost()

    // --- 6-Digit Unique Account Linking & Smart Differential Cache Sync ---

    /**
     * Checks lightweight backend counts & latest timestamps ONLY.
     * Unchanged tables are skipped completely (0 data transfer).
     * Tables with new rows fetch only incremental additions when possible.
     */
    /**
     * Flushes locally queued money accounts, expenses, and memos (created/edited/deleted while offline
     * or during a transient backend failure) before reconciling with remote counts.
     */
    suspend fun flushPendingChanges(coupleId: String) {
        if (coupleId.isBlank() || !clientManager.isConfigured()) return
        val snap = localCache?.getSnapshot(coupleId) ?: return

        if (
            snap.pending_money_account_ids.isEmpty() &&
            snap.deleted_money_account_ids.isEmpty() &&
            snap.pending_expense_ids.isEmpty() &&
            snap.deleted_expense_ids.isEmpty() &&
            snap.pending_memo_ids.isEmpty() &&
            snap.deleted_memo_ids.isEmpty()
        ) {
            return
        }

        val syncedMoneyIds = mutableSetOf<String>()
        val syncedMoneyDeletes = mutableSetOf<String>()
        val syncedExpenseIds = mutableSetOf<String>()
        val syncedExpenseDeletes = mutableSetOf<String>()
        val syncedMemoIds = mutableSetOf<String>()
        val syncedMemoDeletes = mutableSetOf<String>()

        val moneyById = (snap.money_accounts + localMoneyAccounts.filter { it.couple_id == coupleId }).associateBy { it.id }
        for (id in snap.pending_money_account_ids) {
            val acc = moneyById[id]
            if (acc == null) {
                syncedMoneyIds.add(id)
            } else if (runCatching { clientManager.upsertMoneyAccount(acc).isSuccess }.getOrDefault(false)) {
                syncedMoneyIds.add(id)
            }
        }
        for (id in snap.deleted_money_account_ids) {
            if (runCatching { clientManager.deleteMoneyAccount(id).isSuccess }.getOrDefault(false)) {
                syncedMoneyDeletes.add(id)
            }
        }

        val expenseById = (snap.expenses + localExpenses.filter { it.couple_id == coupleId }).associateBy { it.id }
        for (id in snap.pending_expense_ids) {
            val exp = expenseById[id]
            if (exp == null) {
                syncedExpenseIds.add(id)
            } else if (runCatching { clientManager.insertExpense(exp).isSuccess }.getOrDefault(false)) {
                syncedExpenseIds.add(id)
            }
        }
        for (id in snap.deleted_expense_ids) {
            if (runCatching { clientManager.deleteExpense(id).isSuccess }.getOrDefault(false)) {
                syncedExpenseDeletes.add(id)
            }
        }

        val memoById = (snap.memos + localMemos.filter { it.couple_id == coupleId }).associateBy { it.id }
        for (id in snap.pending_memo_ids) {
            val memo = memoById[id]
            if (memo == null) {
                syncedMemoIds.add(id)
            } else if (runCatching { clientManager.insertMemo(memo).isSuccess }.getOrDefault(false)) {
                syncedMemoIds.add(id)
            }
        }
        for (id in snap.deleted_memo_ids) {
            if (runCatching { clientManager.deleteMemo(id).isSuccess }.getOrDefault(false)) {
                syncedMemoDeletes.add(id)
            }
        }

        if (
            syncedMoneyIds.isNotEmpty() ||
            syncedMoneyDeletes.isNotEmpty() ||
            syncedExpenseIds.isNotEmpty() ||
            syncedExpenseDeletes.isNotEmpty() ||
            syncedMemoIds.isNotEmpty() ||
            syncedMemoDeletes.isNotEmpty()
        ) {
            localCache?.updateSnapshot(coupleId) { cur ->
                cur.copy(
                    pending_money_account_ids = cur.pending_money_account_ids - syncedMoneyIds,
                    deleted_money_account_ids = cur.deleted_money_account_ids - syncedMoneyDeletes,
                    pending_expense_ids = cur.pending_expense_ids - syncedExpenseIds,
                    deleted_expense_ids = cur.deleted_expense_ids - syncedExpenseDeletes,
                    pending_memo_ids = cur.pending_memo_ids - syncedMemoIds,
                    deleted_memo_ids = cur.deleted_memo_ids - syncedMemoDeletes
                )
            }
        }
    }

    suspend fun syncChangedTablesOnly(coupleId: String, targetTable: String? = null) {
        if (coupleId.isBlank()) return
        activeCoupleId = coupleId

        if (!clientManager.isConfigured()) {
            hydrateFromLocalCache(coupleId)
            when (targetTable) {
                "expenses" -> refreshExpenses(coupleId)
                "memos" -> refreshMemos(coupleId)
                "profiles" -> refreshProfiles(coupleId)
                "live_locations" -> refreshLiveLocations(coupleId)
                "location_history" -> refreshLocationHistory(coupleId)
                "device_sessions" -> refreshDeviceSessions(coupleId)
                "money_accounts" -> refreshMoneyAccounts(coupleId)
                else -> syncAllCoupleData(coupleId)
            }
            return
        }

        flushPendingChanges(coupleId)

        val countsResult = clientManager.getCoupleSyncCounts(coupleId)
        if (countsResult.isFailure) {
            // Network unavailable: ensure local cache stays displayed cleanly
            hydrateFromLocalCache(coupleId)
            return
        }

        val remote = countsResult.getOrThrow()
        val cachedSnap = localCache?.getSnapshot(coupleId)
        val local = cachedSnap?.counts ?: LocalCoupleCache.computeCounts(
            expenses = _expensesFlow.value,
            memos = _memosFlow.value,
            profiles = _profilesFlow.value,
            liveLocations = _liveLocationsFlow.value,
            locationHistory = _locationHistoryFlow.value,
            deviceSessions = _deviceSessionsFlow.value,
            moneyAccounts = _moneyAccountsFlow.value
        )

        coroutineScope {
            if (targetTable == null || targetTable == "expenses") {
                launch { syncExpensesDelta(coupleId, remote, local) }
            }
            if (targetTable == null || targetTable == "memos") {
                launch { syncMemosDelta(coupleId, remote, local) }
            }
            if (targetTable == null || targetTable == "profiles") {
                launch {
                    if (remote.profiles_count == 0) {
                        val fallbackProfiles = localProfiles.filter { it.couple_id == coupleId }
                        if (_profilesFlow.value != fallbackProfiles) _profilesFlow.value = fallbackProfiles
                        persistProfilesCache(coupleId, fallbackProfiles, "")
                    } else if (
                        remote.profiles_count != local.profiles_count ||
                        remote.profiles_latest != local.profiles_latest ||
                        _profilesFlow.value.isEmpty()
                    ) {
                        refreshProfiles(coupleId, remote.profiles_latest)
                    }
                }
            }
            if (targetTable == null || targetTable == "live_locations") {
                launch {
                    if (remote.live_locations_count == 0) {
                        if (_liveLocationsFlow.value.isNotEmpty()) _liveLocationsFlow.value = emptyList()
                        persistLiveLocationsCache(coupleId, emptyList(), 0L)
                    } else if (
                        remote.live_locations_count != local.live_locations_count ||
                        remote.live_locations_latest != local.live_locations_latest ||
                        _liveLocationsFlow.value.isEmpty()
                    ) {
                        refreshLiveLocations(coupleId, remote.live_locations_latest)
                    }
                }
            }
            if (targetTable == null || targetTable == "location_history") {
                launch { syncLocationHistoryDelta(coupleId, remote, local) }
            }
            if (targetTable == null || targetTable == "device_sessions") {
                launch {
                    if (remote.device_sessions_count == 0) {
                        if (_deviceSessionsFlow.value.isNotEmpty()) _deviceSessionsFlow.value = emptyList()
                        persistDeviceSessionsCache(coupleId, emptyList(), "")
                    } else if (
                        remote.device_sessions_count != local.device_sessions_count ||
                        remote.device_sessions_latest != local.device_sessions_latest ||
                        _deviceSessionsFlow.value.isEmpty()
                    ) {
                        refreshDeviceSessions(coupleId, remote.device_sessions_latest)
                    }
                }
            }
            if (targetTable == null || targetTable == "money_accounts") {
                launch { syncMoneyAccountsDelta(coupleId, remote, local) }
            }
        }
    }

    private suspend fun syncMoneyAccountsDelta(coupleId: String, remote: CoupleSyncCounts, local: CoupleSyncCounts) {
        val snap = localCache?.getSnapshot(coupleId)
        val pendingIds = snap?.pending_money_account_ids.orEmpty()
        val deletedIds = snap?.deleted_money_account_ids.orEmpty()

        val remoteCount = remote.money_accounts_count
        if (remoteCount == null) {
            // Older SQL RPC without money_accounts_count: safely merge from table without wiping cached accounts on failure
            refreshMoneyAccounts(coupleId)
            return
        }
        val remoteLatest = remote.money_accounts_latest ?: 0L
        if (remoteCount == 0) {
            val localOnly = (localMoneyAccounts.filter { it.couple_id == coupleId } + snap?.money_accounts.orEmpty())
                .distinctBy { it.id }
                .filter { it.couple_id == coupleId && it.id in pendingIds && it.id !in deletedIds }
                .sortedByDescending { it.updated_at }
            localMoneyAccounts.removeAll { it.couple_id == coupleId }
            localMoneyAccounts.addAll(localOnly)
            if (_moneyAccountsFlow.value != localOnly) {
                _moneyAccountsFlow.value = localOnly
            }
            persistMoneyAccountsCache(coupleId, localOnly, 0L)
            return
        }
        val current = _moneyAccountsFlow.value
        if (pendingIds.isEmpty() &&
            deletedIds.isEmpty() &&
            remoteCount == local.money_accounts_count &&
            remoteLatest == local.money_accounts_latest &&
            current.isNotEmpty()
        ) {
            return
        }
        refreshMoneyAccounts(coupleId, remoteLatest)
    }

    private suspend fun syncExpensesDelta(coupleId: String, remote: CoupleSyncCounts, local: CoupleSyncCounts) {
        val snap = localCache?.getSnapshot(coupleId)
        val pendingIds = snap?.pending_expense_ids.orEmpty()
        val deletedIds = snap?.deleted_expense_ids.orEmpty()

        if (remote.expenses_count == 0) {
            val localOnly = (localExpenses.filter { it.couple_id == coupleId } + snap?.expenses.orEmpty())
                .distinctBy { it.id }
                .filter { it.couple_id == coupleId && it.id in pendingIds && it.id !in deletedIds }
                .sortedByDescending { it.created_at }
            if (_expensesFlow.value != localOnly) _expensesFlow.value = localOnly
            persistExpensesCache(coupleId, localOnly, "")
            return
        }
        val current = _expensesFlow.value
        if (pendingIds.isEmpty() &&
            deletedIds.isEmpty() &&
            remote.expenses_count == local.expenses_count &&
            remote.expenses_latest == local.expenses_latest &&
            current.isNotEmpty()
        ) {
            return // Unchanged — skip backend call!
        }

        if (pendingIds.isEmpty() &&
            deletedIds.isEmpty() &&
            remote.expenses_count > local.expenses_count &&
            local.expenses_latest.isNotBlank() &&
            current.isNotEmpty()
        ) {
            val deltaRes = clientManager.getExpensesAfter(coupleId, local.expenses_latest)
            if (deltaRes.isSuccess) {
                val delta = deltaRes.getOrThrow()
                val merged = (delta + current).distinctBy { it.id }.sortedByDescending { it.created_at }
                if (merged.size == remote.expenses_count) {
                    if (_expensesFlow.value != merged) _expensesFlow.value = merged
                    persistExpensesCache(coupleId, merged, remote.expenses_latest)
                    return
                }
            }
        }
        refreshExpenses(coupleId, remote.expenses_latest)
    }

    private suspend fun syncMemosDelta(coupleId: String, remote: CoupleSyncCounts, local: CoupleSyncCounts) {
        val snap = localCache?.getSnapshot(coupleId)
        val pendingIds = snap?.pending_memo_ids.orEmpty()
        val deletedIds = snap?.deleted_memo_ids.orEmpty()

        if (remote.memos_count == 0) {
            val localOnly = (localMemos.filter { it.couple_id == coupleId } + snap?.memos.orEmpty())
                .distinctBy { it.id }
                .filter { it.couple_id == coupleId && it.id in pendingIds && it.id !in deletedIds }
                .sortedByDescending { it.created_at }
            if (_memosFlow.value != localOnly) _memosFlow.value = localOnly
            persistMemosCache(coupleId, localOnly, "")
            return
        }
        val current = _memosFlow.value
        if (pendingIds.isEmpty() &&
            deletedIds.isEmpty() &&
            remote.memos_count == local.memos_count &&
            remote.memos_latest == local.memos_latest &&
            current.isNotEmpty()
        ) {
            return // Unchanged — avoid re-downloading Base64 memo photos!
        }

        if (pendingIds.isEmpty() &&
            deletedIds.isEmpty() &&
            remote.memos_count > local.memos_count &&
            local.memos_latest.isNotBlank() &&
            current.isNotEmpty()
        ) {
            val deltaRes = clientManager.getMemosAfter(coupleId, local.memos_latest)
            if (deltaRes.isSuccess) {
                val delta = deltaRes.getOrThrow()
                val merged = (delta + current).distinctBy { it.id }.sortedByDescending { it.created_at }
                if (merged.size == remote.memos_count) {
                    if (_memosFlow.value != merged) _memosFlow.value = merged
                    persistMemosCache(coupleId, merged, remote.memos_latest)
                    return
                }
            }
        }
        refreshMemos(coupleId, remote.memos_latest)
    }

    private suspend fun syncLocationHistoryDelta(coupleId: String, remote: CoupleSyncCounts, local: CoupleSyncCounts) {
        if (remote.location_history_count == 0) {
            if (_locationHistoryFlow.value.isNotEmpty()) _locationHistoryFlow.value = emptyList()
            persistLocationHistoryCache(coupleId, emptyList(), 0L)
            return
        }
        val current = _locationHistoryFlow.value
        if (remote.location_history_count == local.location_history_count &&
            remote.location_history_latest == local.location_history_latest &&
            current.isNotEmpty()
        ) {
            return // Unchanged — skip backend call!
        }

        if (remote.location_history_count > local.location_history_count &&
            local.location_history_latest > 0L &&
            current.isNotEmpty()
        ) {
            val deltaRes = clientManager.getLocationHistoryAfter(coupleId, local.location_history_latest)
            if (deltaRes.isSuccess) {
                val delta = deltaRes.getOrThrow()
                val merged = (delta + current).distinctBy { it.id }.sortedByDescending { it.recorded_at }.take(100)
                if (merged.size == remote.location_history_count || remote.location_history_count >= 100) {
                    if (_locationHistoryFlow.value != merged) _locationHistoryFlow.value = merged
                    persistLocationHistoryCache(coupleId, merged, remote.location_history_latest)
                    return
                }
            }
        }
        refreshLocationHistory(coupleId, remote.location_history_latest)
    }

    suspend fun syncAllCoupleData(coupleId: String) {
        if (coupleId.isBlank()) return
        activeCoupleId = coupleId
        flushPendingChanges(coupleId)
        coroutineScope {
            launch { refreshExpenses(coupleId) }
            launch { refreshProfiles(coupleId) }
            launch { refreshMemos(coupleId) }
            launch { refreshLiveLocations(coupleId) }
            launch { refreshLocationHistory(coupleId) }
            launch { refreshDeviceSessions(coupleId) }
            launch { refreshMoneyAccounts(coupleId) }
        }
    }

    suspend fun generateUniqueLinkCode(
        coupleId: String,
        email: String,
        userName: String
    ): Result<LinkCodeResult> {
        if (coupleId.isBlank()) {
            return Result.failure(IllegalArgumentException("Couple workspace is not initialized yet"))
        }

        if (clientManager.isConfigured()) {
            val rpcResult = clientManager.generateUniqueLinkCode(coupleId, email, userName)
            if (rpcResult.isSuccess) {
                val res = rpcResult.getOrThrow()
                val existingEntry = localCouples.entries.firstOrNull { it.value.id == coupleId }
                if (existingEntry != null) {
                    localCouples.remove(existingEntry.key)
                    localCouples[res.link_code] = existingEntry.value.copy(couple_code = res.link_code)
                }
                return rpcResult
            }
            val err = rpcResult.exceptionOrNull()
            if (err?.message != "RPC_NOT_INSTALLED") {
                return rpcResult
            }
        }

        // Local / Collision-checked unique 6-digit code fallback
        var uniqueCode = ""
        for (attempt in 1..25) {
            val candidate = generateCoupleCode()
            val existsLocally = localCouples.containsKey(candidate) || localLinkCodes.containsKey(candidate)
            val availableRemotely = if (!existsLocally && clientManager.isConfigured()) {
                clientManager.isLinkCodeAvailable(candidate)
            } else {
                true
            }
            if (!existsLocally && availableRemotely) {
                uniqueCode = candidate
                break
            }
        }

        if (uniqueCode.isBlank()) {
            return Result.failure(IllegalStateException("Could not allocate a unique 6-digit code. Please retry."))
        }

        val existingEntry = localCouples.entries.firstOrNull { it.value.id == coupleId }
        val updatedCouple = existingEntry?.value?.copy(couple_code = uniqueCode)
            ?: Couple(id = coupleId, couple_code = uniqueCode, created_at = getTodayIso())
        if (existingEntry != null) {
            localCouples.remove(existingEntry.key)
        }
        localCouples[uniqueCode] = updatedCouple

        localLinkCodes.values.removeAll { it.couple_id == coupleId && !it.is_used }
        localLinkCodes[uniqueCode] = AccountLinkCode(
            id = UUID.randomUUID().toString(),
            link_code = uniqueCode,
            couple_id = coupleId,
            creator_email = email.trim().lowercase(),
            creator_name = userName.trim(),
            is_used = false,
            expires_at = getTodayIso(),
            created_at = getTodayIso()
        )

        localDeviceSessions.replaceAll { ds ->
            if (ds.couple_id == coupleId) ds.copy(couple_code = uniqueCode) else ds
        }

        return Result.success(
            LinkCodeResult(
                success = true,
                message = "Generated unique 6-digit code: $uniqueCode",
                link_code = uniqueCode,
                couple_id = coupleId,
                expires_at = getTodayIso()
            )
        )
    }

    suspend fun linkAccountsWithCode(
        email: String,
        userName: String,
        currentCoupleId: String,
        linkCode: String
    ): Result<AuthResult> {
        val cleanCode = linkCode.trim().uppercase()
        if (cleanCode.length != 6) {
            return Result.failure(IllegalArgumentException("Please enter a valid 6-digit link code"))
        }

        if (clientManager.isConfigured()) {
            val rpcResult = clientManager.linkAccountsWithCode(
                email = email,
                userName = userName,
                currentCoupleId = currentCoupleId,
                linkCode = cleanCode
            )
            if (rpcResult.isSuccess) {
                val res = rpcResult.getOrThrow()
                migrateLocalCoupleData(
                    fromCoupleId = currentCoupleId,
                    toCoupleId = res.couple_id,
                    newCode = res.couple_code,
                    userEmail = email,
                    userName = userName
                )
                syncAllCoupleData(res.couple_id)
                return rpcResult
            }
            val err = rpcResult.exceptionOrNull()
            if (err?.message != "RPC_NOT_INSTALLED") {
                return rpcResult
            }
        }

        // Local / Demo / Fallback resolution & data merge
        val targetLink = localLinkCodes[cleanCode]
        val targetCouple = if (targetLink != null) {
            Couple(id = targetLink.couple_id, couple_code = targetLink.link_code, created_at = targetLink.created_at)
        } else {
            localCouples[cleanCode]
                ?: return Result.failure(NoSuchElementException("Link code '$cleanCode' was not found. Ask your partner for their 6-digit code."))
        }

        if (targetCouple.id != currentCoupleId) {
            migrateLocalCoupleData(
                fromCoupleId = currentCoupleId,
                toCoupleId = targetCouple.id,
                newCode = targetCouple.couple_code,
                userEmail = email,
                userName = userName
            )
        }

        if (targetLink != null) {
            localLinkCodes[cleanCode] = targetLink.copy(
                linked_partner_email = email.trim().lowercase(),
                linked_partner_name = userName.trim(),
                is_used = true
            )
        }

        syncAllCoupleData(targetCouple.id)

        return Result.success(
            AuthResult(
                success = true,
                message = "Accounts linked & synced in database!",
                email = email.trim().lowercase(),
                user_name = userName.trim(),
                couple_id = targetCouple.id,
                couple_code = targetCouple.couple_code
            )
        )
    }

    private fun migrateLocalCoupleData(
        fromCoupleId: String,
        toCoupleId: String,
        newCode: String,
        userEmail: String,
        userName: String
    ) {
        if (fromCoupleId.isBlank() || toCoupleId.isBlank()) return
        if (fromCoupleId != toCoupleId) {
            localExpenses.replaceAll { exp ->
                if (exp.couple_id == fromCoupleId) exp.copy(couple_id = toCoupleId) else exp
            }
            localProfiles.replaceAll { p ->
                if (p.couple_id == fromCoupleId) p.copy(couple_id = toCoupleId) else p
            }
            localMemos.replaceAll { m ->
                if (m.couple_id == fromCoupleId) m.copy(couple_id = toCoupleId) else m
            }
            localLiveLocations.replaceAll { l ->
                if (l.couple_id == fromCoupleId) l.copy(couple_id = toCoupleId) else l
            }
            localLocationHistory.replaceAll { h ->
                if (h.couple_id == fromCoupleId) h.copy(couple_id = toCoupleId) else h
            }
            localDeviceSessions.replaceAll { d ->
                if (d.couple_id == fromCoupleId) d.copy(couple_id = toCoupleId, couple_code = newCode) else d
            }
            localMoneyAccounts.replaceAll { m ->
                if (m.couple_id == fromCoupleId) m.copy(couple_id = toCoupleId) else m
            }
        }
    }

    companion object {
        private val secureRandom = SecureRandom()

        fun generateCoupleCode(): String {
            val number = secureRandom.nextInt(1_000_000)
            return String.format(Locale.US, "%06d", number)
        }

        fun getTodayDate(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return sdf.format(Date())
        }

        fun getTodayIso(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            return sdf.format(Date())
        }
    }
}
