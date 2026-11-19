package com.example.data.repository

import com.example.data.model.AuthState
import com.example.data.model.CoupleMemo
import com.example.data.model.SupabaseDomainError
import com.example.data.model.SupabaseErrorMapper
import com.example.data.supabase.SupabaseClientProvider
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

interface MemoDataSource {
    suspend fun fetchMemos(coupleId: String): List<CoupleMemo>
    suspend fun insertMemo(memo: CoupleMemo): CoupleMemo
    suspend fun deleteMemo(memoId: String, coupleId: String)
}

class SupabaseMemoDataSource(
    private val clientProvider: SupabaseClientProvider
) : MemoDataSource {

    override suspend fun fetchMemos(coupleId: String): List<CoupleMemo> = withContext(Dispatchers.IO) {
        clientProvider.postgrest
            .from("memos")
            .select(Columns.ALL) {
                filter {
                    eq("couple_id", coupleId)
                }
                order("date", Order.DESCENDING)
                order("created_at", Order.DESCENDING)
            }
            .decodeList<CoupleMemo>()
    }

    override suspend fun insertMemo(memo: CoupleMemo): CoupleMemo = withContext(Dispatchers.IO) {
        clientProvider.postgrest
            .from("memos")
            .insert(memo) {
                select()
            }
            .decodeSingle<CoupleMemo>()
    }

    override suspend fun deleteMemo(memoId: String, coupleId: String) = withContext(Dispatchers.IO) {
        clientProvider.postgrest
            .from("memos")
            .delete {
                filter {
                    eq("id", memoId)
                    eq("couple_id", coupleId)
                }
            }
        Unit
    }
}

/**
 * Repository for RLS-protected couple memos with private Storage image cleanup.
 */
class MemoRepository(
    private val memoDataSource: MemoDataSource,
    private val imageStorageRepository: ImageStorageRepository,
    private val authStateProvider: () -> AuthState
) {
    private fun requireVerifiedState(): Result<AuthState.SignedIn> {
        val state = authStateProvider()
        return when (state) {
            is AuthState.SignedIn -> {
                if (state.coupleId.isBlank()) {
                    Result.failure(SupabaseDomainError.AccountNotLinked())
                } else {
                    Result.success(state)
                }
            }
            is AuthState.AccountNotLinked -> Result.failure(SupabaseDomainError.AccountNotLinked())
            is AuthState.SessionExpired -> Result.failure(SupabaseDomainError.SessionExpired())
            else -> Result.failure(SupabaseDomainError.AuthenticationRequired())
        }
    }

    suspend fun getMemos(requestedCoupleId: String? = null): Result<List<CoupleMemo>> {
        val state = requireVerifiedState().getOrElse { return Result.failure(it) }
        if (requestedCoupleId != null && requestedCoupleId.trim() != state.coupleId.trim()) {
            return Result.failure(SupabaseDomainError.PermissionDenied())
        }
        return try {
            val memos = memoDataSource.fetchMemos(state.coupleId)
            if (memos.any { it.couple_id != state.coupleId }) {
                return Result.failure(SupabaseDomainError.PermissionDenied())
            }
            Result.success(memos)
        } catch (e: Exception) {
            Result.failure(SupabaseErrorMapper.mapError(e))
        }
    }

    suspend fun createMemo(
        title: String,
        note: String,
        date: String,
        imagePath: String = "",
        coupleIdOverride: String? = null
    ): Result<CoupleMemo> {
        val state = requireVerifiedState().getOrElse { return Result.failure(it) }
        if (coupleIdOverride != null && coupleIdOverride.trim() != state.coupleId.trim()) {
            return Result.failure(SupabaseDomainError.PermissionDenied())
        }
        if (title.isBlank() && note.isBlank()) {
            return Result.failure(SupabaseDomainError.ValidationError("Please provide a title or note for this memo."))
        }
        if (date.isBlank()) {
            return Result.failure(SupabaseDomainError.ValidationError("Memo date is required."))
        }
        val cleanImagePath = imagePath.trim()
        if (cleanImagePath.isNotEmpty() && !ImageStorageRepository.isValidPrivateObjectPath(cleanImagePath)) {
            return Result.failure(
                SupabaseDomainError.ValidationError("Memo image must reference a private storage object path.")
            )
        }

        val memo = CoupleMemo(
            id = UUID.randomUUID().toString(),
            couple_id = state.coupleId,
            user_name = state.userName.ifBlank { state.email },
            title = title.trim(),
            note = note.trim(),
            image_url = cleanImagePath,
            date = date.trim()
        )
        return try {
            val inserted = memoDataSource.insertMemo(memo)
            Result.success(inserted)
        } catch (e: Exception) {
            Result.failure(SupabaseErrorMapper.mapError(e))
        }
    }

    suspend fun deleteMemo(memo: CoupleMemo): Result<Unit> {
        val state = requireVerifiedState().getOrElse { return Result.failure(it) }
        if (memo.couple_id != state.coupleId) {
            return Result.failure(SupabaseDomainError.PermissionDenied())
        }
        return try {
            memoDataSource.deleteMemo(memo.id, state.coupleId)
            if (ImageStorageRepository.isValidPrivateObjectPath(memo.image_url)) {
                imageStorageRepository.deleteImage(memo.image_url)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(SupabaseErrorMapper.mapError(e))
        }
    }
}
