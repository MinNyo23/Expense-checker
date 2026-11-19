package com.example.data.service

import com.example.data.model.AuthResult
import com.example.data.model.CoupleSyncStatus
import com.example.data.model.LinkCodeResult
import com.example.data.supabase.SupabaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated service responsible for generating unique 6-digit numeric link codes,
 * linking two partner accounts cleanly onto a shared couple workspace in Supabase,
 * and keeping all shared tables synchronized between both linked users.
 */
class AccountLinkingService(
    private val repository: SupabaseRepository
) {
    private val secureRandom = SecureRandom()

    private val _syncStatus = MutableStateFlow(CoupleSyncStatus())
    val syncStatus: StateFlow<CoupleSyncStatus> = _syncStatus.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun generateLocalSixDigitCode(): String {
        val number = secureRandom.nextInt(1_000_000)
        return String.format(Locale.US, "%06d", number)
    }

    fun isValidSixDigitCode(code: String): Boolean {
        return code.trim().matches(Regex("^\\d{6}$"))
    }

    suspend fun generateUniqueLinkCode(
        coupleId: String,
        userEmail: String,
        userName: String
    ): Result<LinkCodeResult> {
        _isSyncing.value = true
        return try {
            val result = repository.generateUniqueLinkCode(
                coupleId = coupleId,
                email = userEmail,
                userName = userName
            )
            result.onSuccess { res ->
                _syncStatus.value = _syncStatus.value.copy(
                    couple_id = res.couple_id,
                    couple_code = res.link_code,
                    last_synced_at = formatNowTime()
                )
            }
            result
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun linkAccountWithCode(
        userEmail: String,
        userName: String,
        currentCoupleId: String,
        linkCode: String
    ): Result<AuthResult> {
        _isSyncing.value = true
        return try {
            val result = repository.linkAccountsWithCode(
                email = userEmail,
                userName = userName,
                currentCoupleId = currentCoupleId,
                linkCode = linkCode
            )
            result.onSuccess { auth ->
                _syncStatus.value = _syncStatus.value.copy(
                    couple_id = auth.couple_id,
                    couple_code = auth.couple_code,
                    is_partner_linked = true,
                    last_synced_at = formatNowTime()
                )
            }
            result
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun syncLinkedUsersData(
        coupleId: String,
        coupleCode: String = "",
        memberNames: List<String> = emptyList(),
        forceFull: Boolean = true
    ) {
        if (coupleId.isBlank()) return
        _isSyncing.value = true
        try {
            if (forceFull) {
                repository.syncAllCoupleData(coupleId)
            } else {
                repository.syncChangedTablesOnly(coupleId)
            }
            val distinctNames = memberNames.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
            _syncStatus.value = CoupleSyncStatus(
                couple_id = coupleId,
                couple_code = coupleCode.ifBlank { _syncStatus.value.couple_code },
                is_partner_linked = distinctNames.size >= 2,
                linked_members_count = distinctNames.size.coerceAtLeast(1),
                partner_names = distinctNames,
                last_synced_at = formatNowTime()
            )
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun syncIfChanged(
        coupleId: String,
        coupleCode: String = "",
        memberNames: List<String> = emptyList()
    ) = syncLinkedUsersData(
        coupleId = coupleId,
        coupleCode = coupleCode,
        memberNames = memberNames,
        forceFull = false
    )

    fun updateSyncParticipants(
        coupleId: String,
        coupleCode: String,
        memberNames: List<String>
    ) {
        val distinctNames = memberNames.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
        val current = _syncStatus.value
        val next = current.copy(
            couple_id = coupleId,
            couple_code = coupleCode,
            is_partner_linked = distinctNames.size >= 2,
            linked_members_count = distinctNames.size.coerceAtLeast(1),
            partner_names = distinctNames
        )
        if (next != current) {
            _syncStatus.value = next
        }
    }

    private fun formatNowTime(): String {
        return SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date())
    }
}
