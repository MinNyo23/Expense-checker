package com.example.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data class representing an entry in the Supabase 'expenses' table.
 *
 * Database schema:
 * - id: uuid (primary key)
 * - couple_id: uuid (foreign key referencing couples.id)
 * - user_name: text (author/payer)
 * - amount: numeric (expense amount)
 * - category: text (category name)
 * - date: text (date formatted as YYYY-MM-DD)
 * - note: text (description/notes)
 * - latitude: double precision (GPS latitude coordinate)
 * - longitude: double precision (GPS longitude coordinate)
 * - created_at: timestamp with time zone (timestamp)
 */
@Serializable
data class Expense(
    @SerialName("id")
    val id: String,

    @SerialName("couple_id")
    val couple_id: String,

    @SerialName("user_name")
    val user_name: String,

    @SerialName("amount")
    val amount: Double,

    @SerialName("category")
    val category: String,

    @SerialName("date")
    val date: String,

    @SerialName("note")
    val note: String = "",

    @SerialName("latitude")
    val latitude: Double? = null,

    @SerialName("longitude")
    val longitude: Double? = null,

    @SerialName("created_at")
    val created_at: String? = null
)
