package com.example.data.model

import kotlinx.serialization.Serializable

/**
 * Lightweight count & latest-change watermark for each synced Supabase table.
 * Only numbers and timestamps are fetched from backend unless a table actually changed.
 */
@Serializable
data class CoupleSyncCounts(
    val expenses_count: Int = 0,
    val expenses_latest: String = "",
    val memos_count: Int = 0,
    val memos_latest: String = "",
    val profiles_count: Int = 0,
    val profiles_latest: String = "",
    val live_locations_count: Int = 0,
    val live_locations_latest: Long = 0L,
    val location_history_count: Int = 0,
    val location_history_latest: Long = 0L,
    val device_sessions_count: Int = 0,
    val device_sessions_latest: String = "",
    val money_accounts_count: Int? = null,
    val money_accounts_latest: Long? = null
)

/**
 * Complete locally persisted snapshot for a couple workspace.
 * Loaded immediately on startup so UI renders from local cache without waiting on network.
 */
@Serializable
data class CachedCoupleSnapshot(
    val couple_id: String = "",
    val expenses: List<Expense> = emptyList(),
    val profiles: List<UserProfile> = emptyList(),
    val memos: List<CoupleMemo> = emptyList(),
    val live_locations: List<LiveLocation> = emptyList(),
    val location_history: List<LocationHistoryItem> = emptyList(),
    val device_sessions: List<DeviceSession> = emptyList(),
    val money_accounts: List<MoneyAccount> = emptyList(),
    val counts: CoupleSyncCounts = CoupleSyncCounts(),
    val last_synced_at_ms: Long = 0L,
    val pending_money_account_ids: Set<String> = emptySet(),
    val deleted_money_account_ids: Set<String> = emptySet(),
    val pending_expense_ids: Set<String> = emptySet(),
    val deleted_expense_ids: Set<String> = emptySet(),
    val pending_memo_ids: Set<String> = emptySet(),
    val deleted_memo_ids: Set<String> = emptySet()
)
