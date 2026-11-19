package com.example.data.model

/**
 * Defines how an expense is split between the couple.
 */
enum class SplitType(
    val code: String,
    val title: String,
    val shortLabel: String,
    val description: String
) {
    SHARED("50/50", "Shared (50/50 Split)", "50/50", "Cost is split equally between both partners"),
    PAID_FOR_PARTNER("100_PARTNER", "Paid 100% for Partner", "100% Partner", "The other partner owes the full amount"),
    PERSONAL("PERSONAL", "Personal (0% Split)", "Personal", "Personal cost; does not affect partner balance"),
    SETTLEMENT("SETTLE", "Settle Up Balance", "Settle Up", "Payment made to square up previous balances");

    companion object {
        fun fromCode(code: String?): SplitType {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: SHARED
        }
    }
}
