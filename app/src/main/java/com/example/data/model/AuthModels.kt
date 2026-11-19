package com.example.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SupabaseAuthUser(
    val id: String = "",
    val email: String = "",
    val user_metadata: JsonObject? = null
)

@Serializable
data class SupabaseAuthSessionResponse(
    val access_token: String = "",
    val token_type: String = "bearer",
    val expires_in: Long = 3600L,
    val expires_at: Long = 0L,
    val refresh_token: String = "",
    val user: SupabaseAuthUser? = null
)

@Serializable
data class AuthSessionInfo(
    val authUserId: String,
    val email: String,
    val expiresAtEpochSec: Long = 0L
)

@Serializable
data class AuthResult(
    val success: Boolean = false,
    val message: String = "",
    val auth_user_id: String = "",
    val email: String = "",
    val user_name: String = "",
    val couple_id: String = "",
    val couple_code: String = "",
    val requiresEmailConfirmation: Boolean = false,
    val accountPendingLink: Boolean = false
)

@Serializable
data class AccountLinkCode(
    val id: String = "",
    val link_code: String,
    val couple_id: String,
    val creator_email: String = "",
    val creator_name: String = "",
    val linked_partner_email: String = "",
    val linked_partner_name: String = "",
    val is_used: Boolean = false,
    val expires_at: String = "",
    val created_at: String = ""
)

@Serializable
data class LinkCodeResult(
    val success: Boolean = false,
    val message: String = "",
    val link_code: String = "",
    val couple_id: String = "",
    val expires_at: String = ""
)

@Serializable
data class CoupleSyncStatus(
    val couple_id: String = "",
    val couple_code: String = "",
    val is_partner_linked: Boolean = false,
    val linked_members_count: Int = 1,
    val partner_names: List<String> = emptyList(),
    val last_synced_at: String = ""
)
