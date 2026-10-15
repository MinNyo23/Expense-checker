package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Expense(
    val id: String,
    val couple_id: String,
    val user_name: String,
    val amount: Double,
    val category: String,
    val date: String,
    val note: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val created_at: String? = null
)
