package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RecurringBill(
    val id: String,
    val title: String,
    val amount: Double,
    val category: String,
    val dueDayOfMonth: Int,
    val defaultPayer: String = ""
)
