package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceSession(
    val id: String = "",
    val device_id: String,
    val couple_id: String,
    val couple_code: String,
    val user_name: String,
    val email: String = "",
    val device_name: String = "",
    val is_active: Boolean = true,
    val last_login_at: String = ""
)
