package com.example.data.repository

import com.example.data.local.CouplePreferences
import com.example.data.model.SupabaseDomainError
import com.example.data.model.SupabaseErrorMapper
import com.example.data.model.UserProfile
import com.example.data.supabase.SupabaseClientProvider
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

interface ProfileDataSource {
    suspend fun fetchProfileByAuthUserId(authUserId: String): UserProfile?
    suspend fun fetchProfilesByCoupleId(coupleId: String): List<UserProfile>
    suspend fun updateOwnProfileFields(
        profileId: String,
        authUserId: String,
        userName: String,
        dateOfBirth: String,
        avatarUrl: String
    ): UserProfile
}

class SupabaseProfileDataSource(
    private val clientProvider: SupabaseClientProvider
) : ProfileDataSource {

    @Serializable
    private data class ProfileEditableUpdate(
        val user_name: String,
        val date_of_birth: String,
        val avatar_url: String
    )

    override suspend fun fetchProfileByAuthUserId(authUserId: String): UserProfile? = withContext(Dispatchers.IO) {
        val cleanUid = authUserId.trim()
        if (cleanUid.isBlank()) return@withContext null
        val list = clientProvider.postgrest
            .from("profiles")
            .select(Columns.ALL) {
                filter {
                    eq("auth_user_id", cleanUid)
                }
                limit(1)
            }
            .decodeList<UserProfile>()
        list.firstOrNull()
    }

    override suspend fun fetchProfilesByCoupleId(coupleId: String): List<UserProfile> = withContext(Dispatchers.IO) {
        val cleanCoupleId = coupleId.trim()
        if (cleanCoupleId.isBlank()) return@withContext emptyList()
        clientProvider.postgrest
            .from("profiles")
            .select(Columns.ALL) {
                filter {
                    eq("couple_id", cleanCoupleId)
                }
            }
            .decodeList<UserProfile>()
    }

    override suspend fun updateOwnProfileFields(
        profileId: String,
        authUserId: String,
        userName: String,
        dateOfBirth: String,
        avatarUrl: String
    ): UserProfile = withContext(Dispatchers.IO) {
        clientProvider.postgrest
            .from("profiles")
            .update(
                ProfileEditableUpdate(
                    user_name = userName.trim(),
                    date_of_birth = dateOfBirth.trim(),
                    avatar_url = avatarUrl.trim()
                )
            ) {
                filter {
                    eq("id", profileId.trim())
                    eq("auth_user_id", authUserId.trim())
                }
                select()
            }
            .decodeSingle<UserProfile>()
    }
}

/**
 * Handles loading and validating the authenticated user's profile and linked couple_id.
 * Never writes profiles.auth_user_id and never bypasses RLS.
 */
class ProfileRepository(
    private val dataSource: ProfileDataSource,
    private val preferences: CouplePreferences? = null
) {
    /**
     * Loads the profile linked to [authUserId] and confirms:
     * 1. profile != null
     * 2. profile.auth_user_id matches [authUserId]
     * 3. profile.couple_id is non-blank
     *
     * Caches ONLY non-sensitive profile metadata when verified.
     * Returns Result.success(null) when the account has no linked profile yet.
     */
    suspend fun loadVerifiedProfile(authUserId: String): Result<UserProfile?> {
        val cleanUid = authUserId.trim()
        if (cleanUid.isBlank()) {
            return Result.failure(SupabaseDomainError.AuthenticationRequired())
        }
        return try {
            val profile = dataSource.fetchProfileByAuthUserId(cleanUid)
            if (profile == null ||
                profile.auth_user_id.trim() != cleanUid ||
                profile.couple_id.isBlank()
            ) {
                Result.success(null)
            } else {
                preferences?.cacheNonSensitiveProfile(
                    userName = profile.user_name,
                    email = profile.email,
                    coupleId = profile.couple_id,
                    dateOfBirth = profile.date_of_birth,
                    avatarUrl = profile.avatar_url
                )
                Result.success(profile)
            }
        } catch (e: Exception) {
            Result.failure(SupabaseErrorMapper.mapError(e))
        }
    }

    suspend fun getCoupleProfiles(verifiedCoupleId: String): Result<List<UserProfile>> {
        val cleanCoupleId = verifiedCoupleId.trim()
        if (cleanCoupleId.isBlank()) {
            return Result.failure(SupabaseDomainError.AccountNotLinked())
        }
        return try {
            val profiles = dataSource.fetchProfilesByCoupleId(cleanCoupleId)
                .filter { it.couple_id == cleanCoupleId }
            Result.success(profiles)
        } catch (e: Exception) {
            Result.failure(SupabaseErrorMapper.mapError(e))
        }
    }

    suspend fun updateOwnProfile(
        profile: UserProfile,
        authUserId: String,
        newName: String,
        newDob: String,
        newAvatarUrl: String
    ): Result<UserProfile> {
        val cleanUid = authUserId.trim()
        if (cleanUid.isBlank()) {
            return Result.failure(SupabaseDomainError.AuthenticationRequired())
        }
        if (profile.auth_user_id.trim() != cleanUid || profile.id.isBlank()) {
            return Result.failure(SupabaseDomainError.PermissionDenied())
        }
        if (newName.isBlank()) {
            return Result.failure(SupabaseDomainError.ValidationError("Display name cannot be empty."))
        }
        return try {
            val updated = dataSource.updateOwnProfileFields(
                profileId = profile.id,
                authUserId = cleanUid,
                userName = newName,
                dateOfBirth = newDob,
                avatarUrl = newAvatarUrl
            )
            preferences?.cacheNonSensitiveProfile(
                userName = updated.user_name,
                email = updated.email,
                coupleId = updated.couple_id,
                dateOfBirth = updated.date_of_birth,
                avatarUrl = updated.avatar_url
            )
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(SupabaseErrorMapper.mapError(e))
        }
    }
}
