package com.example.data.repository

import com.example.data.model.AuthState
import com.example.data.model.Expense
import com.example.data.model.SupabaseDomainError
import com.example.data.model.SupabaseErrorMapper
import com.example.data.supabase.SupabaseClientProvider
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ExpenseDataSource {
    suspend fun fetchExpenses(coupleId: String): List<Expense>
    suspend fun insertExpense(expense: Expense): Expense
    suspend fun deleteExpense(expenseId: String, coupleId: String)
}

class SupabaseExpenseDataSource(
    private val clientProvider: SupabaseClientProvider
) : ExpenseDataSource {

    override suspend fun fetchExpenses(coupleId: String): List<Expense> = withContext(Dispatchers.IO) {
        clientProvider.postgrest
            .from("expenses")
            .select(Columns.ALL) {
                filter {
                    eq("couple_id", coupleId)
                }
                order("date", Order.DESCENDING)
                order("created_at", Order.DESCENDING)
            }
            .decodeList<Expense>()
    }

    override suspend fun insertExpense(expense: Expense): Expense = withContext(Dispatchers.IO) {
        clientProvider.postgrest
            .from("expenses")
            .insert(expense) {
                select()
            }
            .decodeSingle<Expense>()
    }

    override suspend fun deleteExpense(expenseId: String, coupleId: String) = withContext(Dispatchers.IO) {
        clientProvider.postgrest
            .from("expenses")
            .delete {
                filter {
                    eq("id", expenseId)
                    eq("couple_id", coupleId)
                }
            }
        Unit
    }
}

/**
 * Repository for RLS-protected couple expenses.
 * Rejects signed-out calls and enforces couple_id matching the authenticated user's profile.
 */
class ExpenseRepository(
    private val dataSource: ExpenseDataSource,
    private val authStateProvider: () -> AuthState
) {
    private fun requireVerifiedCoupleId(requestedCoupleId: String? = null): Result<String> {
        val state = authStateProvider()
        if (state !is AuthState.SignedIn) {
            return when (state) {
                is AuthState.AccountNotLinked -> Result.failure(SupabaseDomainError.AccountNotLinked())
                is AuthState.SessionExpired -> Result.failure(SupabaseDomainError.SessionExpired())
                else -> Result.failure(SupabaseDomainError.AuthenticationRequired())
            }
        }
        val verifiedCoupleId = state.coupleId.trim()
        if (verifiedCoupleId.isBlank()) {
            return Result.failure(SupabaseDomainError.AccountNotLinked())
        }
        if (requestedCoupleId != null && requestedCoupleId.trim() != verifiedCoupleId) {
            return Result.failure(SupabaseDomainError.PermissionDenied())
        }
        return Result.success(verifiedCoupleId)
    }

    suspend fun getExpenses(requestedCoupleId: String? = null): Result<List<Expense>> {
        val verifiedCoupleId = requireVerifiedCoupleId(requestedCoupleId).getOrElse {
            return Result.failure(it)
        }
        return try {
            val rows = dataSource.fetchExpenses(verifiedCoupleId)
            // Defense-in-depth: never return rows belonging to another couple_id
            if (rows.any { it.couple_id != verifiedCoupleId }) {
                return Result.failure(SupabaseDomainError.PermissionDenied())
            }
            Result.success(rows)
        } catch (e: Exception) {
            Result.failure(SupabaseErrorMapper.mapError(e))
        }
    }

    suspend fun addExpense(expense: Expense): Result<Expense> {
        val verifiedCoupleId = requireVerifiedCoupleId(expense.couple_id).getOrElse {
            return Result.failure(it)
        }
        if (expense.amount <= 0.0 || expense.amount.isNaN() || expense.amount.isInfinite()) {
            return Result.failure(SupabaseDomainError.ValidationError("Expense amount must be greater than zero."))
        }
        if (expense.category.isBlank()) {
            return Result.failure(SupabaseDomainError.ValidationError("Please select an expense category."))
        }
        if (expense.date.isBlank()) {
            return Result.failure(SupabaseDomainError.ValidationError("Expense date is required."))
        }
        if (expense.user_name.isBlank()) {
            return Result.failure(SupabaseDomainError.ValidationError("Author name is required."))
        }
        return try {
            val scoped = expense.copy(couple_id = verifiedCoupleId)
            val inserted = dataSource.insertExpense(scoped)
            Result.success(inserted)
        } catch (e: Exception) {
            Result.failure(SupabaseErrorMapper.mapError(e))
        }
    }

    suspend fun deleteExpense(expenseId: String, coupleId: String? = null): Result<Unit> {
        val verifiedCoupleId = requireVerifiedCoupleId(coupleId).getOrElse {
            return Result.failure(it)
        }
        if (expenseId.isBlank()) {
            return Result.failure(SupabaseDomainError.ValidationError("Expense ID is required."))
        }
        return try {
            dataSource.deleteExpense(expenseId, verifiedCoupleId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(SupabaseErrorMapper.mapError(e))
        }
    }
}
