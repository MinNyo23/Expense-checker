package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String = "",
    val couple_id: String,
    val user_name: String,
    val email: String = "",
    val auth_user_id: String = "",
    val date_of_birth: String = "",
    val avatar_url: String = "",
    val role: String = "partner",
    val updated_at: String? = null
)
