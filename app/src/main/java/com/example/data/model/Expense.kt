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
 * - note: text (description/notes, optionally embedded with [split:...] and [photo:...])
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
) {
    /**
     * Clean note text without embedded tags for display in UI.
     */
    val cleanNote: String
        get() = note
            .replace(SPLIT_REGEX, "")
            .replace(PHOTO_REGEX, "")
            .replace(ACCOUNT_ID_REGEX, "")
            .replace(ACCOUNT_NAME_REGEX, "")
            .trim()

    /**
     * Extracted split type from note metadata. Defaults to SHARED.
     */
    val splitType: SplitType
        get() {
            val match = SPLIT_REGEX.find(note)
            return if (match != null) {
                SplitType.fromCode(match.groupValues[1])
            } else {
                SplitType.SHARED
            }
        }

    /**
     * Extracted attached photo URI/file path from note metadata.
     */
    val photoUri: String?
        get() {
            val match = PHOTO_REGEX.find(note)
            return match?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        }

    /**
     * Extracted MoneyAccount ID linked to this expense (if paid from Cash, Bank, or Card).
     */
    val moneyAccountId: String?
        get() {
            val match = ACCOUNT_ID_REGEX.find(note)
            return match?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        }

    /**
     * Extracted short account label (e.g. "Cash", "KBZ Bank (1234)") linked to this expense.
     */
    val moneyAccountLabel: String?
        get() {
            val match = ACCOUNT_NAME_REGEX.find(note)
            return match?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        }

    companion object {
        private val SPLIT_REGEX = Regex("""\[split:([^]]*)\]""")
        private val PHOTO_REGEX = Regex("""\[photo:([^]]*)\]""")
        private val ACCOUNT_ID_REGEX = Regex("""\[acct:([^]]*)\]""")
        private val ACCOUNT_NAME_REGEX = Regex("""\[acctname:([^]]*)\]""")

        fun buildNote(
            userNote: String,
            splitType: SplitType,
            photoPath: String? = null,
            accountId: String? = null,
            accountLabel: String? = null
        ): String {
            val stripped = userNote
                .replace(SPLIT_REGEX, "")
                .replace(PHOTO_REGEX, "")
                .replace(ACCOUNT_ID_REGEX, "")
                .replace(ACCOUNT_NAME_REGEX, "")
                .trim()
            val tokens = mutableListOf<String>()
            if (stripped.isNotBlank()) {
                tokens.add(stripped)
            }
            if (splitType != SplitType.SHARED) {
                tokens.add("[split:${splitType.code}]")
            }
            if (!photoPath.isNullOrBlank()) {
                tokens.add("[photo:$photoPath]")
            }
            if (!accountId.isNullOrBlank()) {
                tokens.add("[acct:$accountId]")
            }
            if (!accountLabel.isNullOrBlank()) {
                val safeLabel = accountLabel.replace("[", "(").replace("]", ")").trim()
                if (safeLabel.isNotBlank()) {
                    tokens.add("[acctname:$safeLabel]")
                }
            }
            return tokens.joinToString(" ").trim()
        }
    }
}
