package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SavingsGoal(
    val id: String,
    val title: String,
    val targetAmount: Double,
    val currentAmount: Double = 0.0,
    val targetDate: String = "",
    val categoryIcon: String = "Favorite"
) {
    val progressPercentage: Float
        get() = if (targetAmount > 0) ((currentAmount / targetAmount * 100.0).coerceIn(0.0, 100.0)).toFloat() else 0f

    val isCompleted: Boolean
        get() = currentAmount >= targetAmount && targetAmount > 0

    val remainingAmount: Double
        get() = (targetAmount - currentAmount).coerceAtLeast(0.0)
}
