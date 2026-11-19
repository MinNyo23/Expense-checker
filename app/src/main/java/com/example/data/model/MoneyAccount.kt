package com.example.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class MoneyAccountType(val label: String, val pluralLabel: String) {
    CASH("Cash", "Cash Wallets"),
    BANK("Bank", "Bank Accounts"),
    CARD("Card", "Cards");

    companion object {
        fun fromString(value: String): MoneyAccountType {
            return entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: CASH
        }
    }
}

/**
 * Separated per-person money account (Cash, Bank Account, or Card) synced with Supabase.
 * Each account belongs strictly to either the user or their partner/girlfriend.
 */
@Serializable
data class MoneyAccount(
    @SerialName("id")
    val id: String,

    @SerialName("couple_id")
    val couple_id: String,

    @SerialName("owner_name")
    val owner_name: String,

    @SerialName("owner_email")
    val owner_email: String = "",

    @SerialName("account_type")
    val account_type: String = MoneyAccountType.CASH.name,

    @SerialName("account_name")
    val account_name: String,

    @SerialName("institution_name")
    val institution_name: String = "",

    @SerialName("account_number_last4")
    val account_number_last4: String = "",

    @SerialName("balance")
    val balance: Double = 0.0,

    @SerialName("initial_balance")
    val initial_balance: Double = 0.0,

    @SerialName("note")
    val note: String = "",

    @SerialName("updated_at")
    val updated_at: Long = 0L
) {
    val typeEnum: MoneyAccountType
        get() = MoneyAccountType.fromString(account_type)

    val displayTitle: String
        get() = when {
            institution_name.isNotBlank() && !account_name.equals(institution_name, ignoreCase = true) ->
                "$institution_name • $account_name"
            else -> account_name.ifBlank { institution_name.ifBlank { typeEnum.label } }
        }

    val maskedNumber: String
        get() {
            val clean = account_number_last4.filter { it.isDigit() }.takeLast(4)
            return if (clean.isNotBlank()) "•••• $clean" else ""
        }

    val shortBadgeLabel: String
        get() {
            val base = when (typeEnum) {
                MoneyAccountType.CASH -> account_name.ifBlank { "Cash" }
                MoneyAccountType.BANK -> institution_name.ifBlank { account_name }
                MoneyAccountType.CARD -> institution_name.ifBlank { account_name }
            }
            val suffix = account_number_last4.filter { it.isDigit() }.takeLast(4)
            return if (suffix.isNotBlank()) "$base ($suffix)" else base
        }

    fun matchesOwner(
        targetName: String,
        targetEmail: String = "",
        extraNames: List<String> = emptyList()
    ): Boolean {
        val cleanTargetEmail = targetEmail.trim()
        val cleanOwnerEmail = owner_email.trim()
        if (cleanTargetEmail.isNotBlank() && cleanOwnerEmail.isNotBlank()) {
            return cleanOwnerEmail.equals(cleanTargetEmail, ignoreCase = true)
        }
        val cleanOwnerName = owner_name.trim()
        if (cleanOwnerName.isBlank()) return false
        val candidates = (listOf(targetName) + extraNames)
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return candidates.any { cleanOwnerName.equals(it, ignoreCase = true) }
    }
}

@Serializable
data class OwnerMoneySummary(
    val ownerName: String = "",
    val totalBalance: Double = 0.0,
    val cashBalance: Double = 0.0,
    val bankBalance: Double = 0.0,
    val cardBalance: Double = 0.0,
    val cashCount: Int = 0,
    val bankCount: Int = 0,
    val cardCount: Int = 0
) {
    val totalAccounts: Int get() = cashCount + bankCount + cardCount
    val cashAndBankBalance: Double get() = cashBalance + bankBalance

    companion object {
        fun fromAccounts(ownerName: String, accounts: List<MoneyAccount>): OwnerMoneySummary {
            var cashBal = 0.0
            var bankBal = 0.0
            var cardBal = 0.0
            var cashCnt = 0
            var bankCnt = 0
            var cardCnt = 0

            for (acc in accounts) {
                when (acc.typeEnum) {
                    MoneyAccountType.CASH -> {
                        cashBal += acc.balance
                        cashCnt++
                    }
                    MoneyAccountType.BANK -> {
                        bankBal += acc.balance
                        bankCnt++
                    }
                    MoneyAccountType.CARD -> {
                        cardBal += acc.balance
                        cardCnt++
                    }
                }
            }

            return OwnerMoneySummary(
                ownerName = ownerName,
                totalBalance = cashBal + bankBal + cardBal,
                cashBalance = cashBal,
                bankBalance = bankBal,
                cardBalance = cardBal,
                cashCount = cashCnt,
                bankCount = bankCnt,
                cardCount = cardCnt
            )
        }
    }
}
