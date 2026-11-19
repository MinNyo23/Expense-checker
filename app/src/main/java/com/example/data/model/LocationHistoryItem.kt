package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LocationHistoryItem(
    val id: String = "",
    val couple_id: String,
    val user_name: String,
    val latitude: Double,
    val longitude: Double,
    val place_name: String = "",
    val timestamp: String = "",
    val date: String = "",
    val recorded_at: Long = System.currentTimeMillis()
)
