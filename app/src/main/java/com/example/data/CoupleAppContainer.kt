package com.example.data

import android.content.Context
import com.example.data.local.CouplePreferences
import com.example.data.local.LocalCoupleCache
import com.example.data.repository.AuthRepository
import com.example.data.repository.ExpenseRepository
import com.example.data.repository.ImageStorageRepository
import com.example.data.repository.MemoRepository
import com.example.data.repository.ProfileRepository
import com.example.data.repository.SupabaseAuthDataSource
import com.example.data.repository.SupabaseExpenseDataSource
import com.example.data.repository.SupabaseImageStorageDataSource
import com.example.data.repository.SupabaseMemoDataSource
import com.example.data.repository.SupabaseProfileDataSource
import com.example.data.service.AccountLinkingService
import com.example.data.supabase.SupabaseClientManager
import com.example.data.supabase.SupabaseClientProvider
import com.example.data.supabase.SupabaseRepository

/**
 * Shared singleton dependency container providing one reusable SupabaseClientProvider
 * and typed repositories across ViewModel and background LocationTrackingService.
 */
object CoupleAppContainer {
    @Volatile
    private var preferencesInstance: CouplePreferences? = null

    @Volatile
    private var localCacheInstance: LocalCoupleCache? = null

    @Volatile
    private var clientProviderInstance: SupabaseClientProvider? = null

    @Volatile
    private var profileRepositoryInstance: ProfileRepository? = null

    @Volatile
    private var authRepositoryInstance: AuthRepository? = null

    @Volatile
    private var imageStorageRepositoryInstance: ImageStorageRepository? = null

    @Volatile
    private var expenseRepositoryInstance: ExpenseRepository? = null

    @Volatile
    private var memoRepositoryInstance: MemoRepository? = null

    @Volatile
    private var clientManagerInstance: SupabaseClientManager? = null

    @Volatile
    private var repositoryInstance: SupabaseRepository? = null

    @Volatile
    private var linkingServiceInstance: AccountLinkingService? = null

    fun getPreferences(context: Context): CouplePreferences {
        return preferencesInstance ?: synchronized(this) {
            preferencesInstance ?: CouplePreferences(context.applicationContext).also {
                preferencesInstance = it
            }
        }
    }

    fun getLocalCache(context: Context): LocalCoupleCache {
        return localCacheInstance ?: synchronized(this) {
            localCacheInstance ?: LocalCoupleCache(context.applicationContext).also {
                localCacheInstance = it
            }
        }
    }

    fun getClientProvider(): SupabaseClientProvider {
        return clientProviderInstance ?: synchronized(this) {
            clientProviderInstance ?: SupabaseClientProvider().also {
                clientProviderInstance = it
            }
        }
    }

    fun getProfileRepository(context: Context): ProfileRepository {
        return profileRepositoryInstance ?: synchronized(this) {
            profileRepositoryInstance ?: ProfileRepository(
                dataSource = SupabaseProfileDataSource(getClientProvider()),
                preferences = getPreferences(context)
            ).also {
                profileRepositoryInstance = it
            }
        }
    }

    fun getAuthRepository(context: Context): AuthRepository {
        return authRepositoryInstance ?: synchronized(this) {
            authRepositoryInstance ?: AuthRepository(
                authDataSource = SupabaseAuthDataSource(getClientProvider()),
                profileRepository = getProfileRepository(context)
            ).also {
                authRepositoryInstance = it
            }
        }
    }

    fun getImageStorageRepository(context: Context): ImageStorageRepository {
        val authRepo = getAuthRepository(context)
        return imageStorageRepositoryInstance ?: synchronized(this) {
            imageStorageRepositoryInstance ?: ImageStorageRepository(
                dataSource = SupabaseImageStorageDataSource(getClientProvider()),
                authStateProvider = { authRepo.currentAuthState }
            ).also {
                imageStorageRepositoryInstance = it
            }
        }
    }

    fun getExpenseRepository(context: Context): ExpenseRepository {
        val authRepo = getAuthRepository(context)
        return expenseRepositoryInstance ?: synchronized(this) {
            expenseRepositoryInstance ?: ExpenseRepository(
                dataSource = SupabaseExpenseDataSource(getClientProvider()),
                authStateProvider = { authRepo.currentAuthState }
            ).also {
                expenseRepositoryInstance = it
            }
        }
    }

    fun getMemoRepository(context: Context): MemoRepository {
        val authRepo = getAuthRepository(context)
        return memoRepositoryInstance ?: synchronized(this) {
            memoRepositoryInstance ?: MemoRepository(
                memoDataSource = SupabaseMemoDataSource(getClientProvider()),
                imageStorageRepository = getImageStorageRepository(context),
                authStateProvider = { authRepo.currentAuthState }
            ).also {
                memoRepositoryInstance = it
            }
        }
    }

    fun getClientManager(context: Context? = null): SupabaseClientManager {
        return clientManagerInstance ?: synchronized(this) {
            clientManagerInstance ?: SupabaseClientManager(
                appContext = context?.applicationContext,
                clientProvider = getClientProvider()
            ).also {
                clientManagerInstance = it
            }
        }
    }

    fun getRepository(context: Context): SupabaseRepository {
        return repositoryInstance ?: synchronized(this) {
            repositoryInstance ?: SupabaseRepository(
                clientManager = getClientManager(context),
                localCache = getLocalCache(context)
            ).also {
                repositoryInstance = it
            }
        }
    }

    fun getLinkingService(context: Context): AccountLinkingService {
        return linkingServiceInstance ?: synchronized(this) {
            linkingServiceInstance ?: AccountLinkingService(getRepository(context)).also {
                linkingServiceInstance = it
            }
        }
    }
}
