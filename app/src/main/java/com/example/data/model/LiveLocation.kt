package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LiveLocation(
    val id: String = "",
    val couple_id: String,
    val user_name: String,
    val latitude: Double,
    val longitude: Double,
    val is_sharing: Boolean = true,
    val battery_level: Int? = null,
    val place_name: String = "",
    val timestamp: String = "",
    val updated_at: Long = System.currentTimeMillis()
)
