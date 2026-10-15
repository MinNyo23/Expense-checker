package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Couple(
    val id: String,
    val couple_code: String,
    val created_at: String? = null
)
