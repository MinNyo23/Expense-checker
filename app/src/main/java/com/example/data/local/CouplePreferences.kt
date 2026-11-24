package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "couple_prefs")

/**
 * Non-sensitive cached session & UI preference metadata.
 * Authentication tokens and secrets are NEVER stored here — they are managed
 * exclusively by the official Supabase Auth SDK.
 */
data class UserSession(
    val userName: String = "",
    val username: String = "",
    val coupleId: String = "",
    val coupleCode: String = "",
    val isPaired: Boolean = false,
    val relationshipStartDate: String = "",
    val monthlyBudget: Double = 1000000.0,
    val savingsGoalsJson: String = "",
    val recurringBillsJson: String = "",
    val movieWatchlistJson: String = "",
    val appPin: String = "",
    val isScreenSecurityEnabled: Boolean = false,
    val userEmail: String = "",
    val dateOfBirth: String = "",
    val avatarPath: String = "",
    val partnerName: String = "",
    val partnerEmail: String = "",
    val partnerDob: String = "",
    val partnerAvatarUrl: String = "",
    val isLocationSharingEnabled: Boolean = false,
    val hasExplicitlySetLocationSharing: Boolean = false,
    val hasPromptedPermissions: Boolean = false,
    val authUserId: String = ""
) {
    val isPinSet: Boolean get() = appPin.isNotBlank() && appPin.length >= 4
}

class CouplePreferences(private val context: Context) {

    companion object {
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_COUPLE_ID = stringPreferencesKey("couple_id")
        val KEY_COUPLE_CODE = stringPreferencesKey("couple_code")
        val KEY_IS_PAIRED = booleanPreferencesKey("is_paired")
        val KEY_LOVE_START_DATE = stringPreferencesKey("love_start_date")
        val KEY_MONTHLY_BUDGET = doublePreferencesKey("monthly_budget")
        val KEY_SAVINGS_GOALS = stringPreferencesKey("savings_goals")
        val KEY_RECURRING_BILLS = stringPreferencesKey("recurring_bills")
        val KEY_MOVIE_WATCHLIST = stringPreferencesKey("movie_watchlist")
        val KEY_APP_PIN = stringPreferencesKey("app_pin")
        val KEY_SCREEN_SECURITY = booleanPreferencesKey("screen_security")
        val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        val KEY_DATE_OF_BIRTH = stringPreferencesKey("date_of_birth")
        val KEY_AVATAR_PATH = stringPreferencesKey("avatar_path")
        val KEY_PARTNER_NAME = stringPreferencesKey("partner_name")
        val KEY_PARTNER_EMAIL = stringPreferencesKey("partner_email")
        val KEY_PARTNER_DOB = stringPreferencesKey("partner_dob")
        val KEY_PARTNER_AVATAR_URL = stringPreferencesKey("partner_avatar_url")
        val KEY_LOCATION_SHARING = booleanPreferencesKey("location_sharing")
        val KEY_LOCATION_SHARING_USER_SET = booleanPreferencesKey("location_sharing_user_set")
        val KEY_PERMISSION_PROMPTED = booleanPreferencesKey("permission_prompted")
        val KEY_SUPABASE_AUTH_USER_ID = stringPreferencesKey("supabase_auth_user_id")
    }

    val userSessionFlow: Flow<UserSession> = context.dataStore.data.map { preferences ->
        UserSession(
            userName = preferences[KEY_USER_NAME] ?: "",
            username = preferences[KEY_USERNAME] ?: "",
            coupleId = preferences[KEY_COUPLE_ID] ?: "",
            coupleCode = preferences[KEY_COUPLE_CODE] ?: "",
            isPaired = preferences[KEY_IS_PAIRED] ?: false,
            relationshipStartDate = preferences[KEY_LOVE_START_DATE] ?: "",
            monthlyBudget = preferences[KEY_MONTHLY_BUDGET] ?: 1000000.0,
            savingsGoalsJson = preferences[KEY_SAVINGS_GOALS] ?: "",
            recurringBillsJson = preferences[KEY_RECURRING_BILLS] ?: "",
            movieWatchlistJson = preferences[KEY_MOVIE_WATCHLIST] ?: "",
            appPin = preferences[KEY_APP_PIN] ?: "",
            isScreenSecurityEnabled = preferences[KEY_SCREEN_SECURITY] ?: false,
            userEmail = preferences[KEY_USER_EMAIL] ?: "",
            dateOfBirth = preferences[KEY_DATE_OF_BIRTH] ?: "",
            avatarPath = preferences[KEY_AVATAR_PATH] ?: "",
            partnerName = preferences[KEY_PARTNER_NAME] ?: "",
            partnerEmail = preferences[KEY_PARTNER_EMAIL] ?: "",
            partnerDob = preferences[KEY_PARTNER_DOB] ?: "",
            partnerAvatarUrl = preferences[KEY_PARTNER_AVATAR_URL] ?: "",
            isLocationSharingEnabled = preferences[KEY_LOCATION_SHARING] ?: false,
            hasExplicitlySetLocationSharing = preferences[KEY_LOCATION_SHARING_USER_SET] ?: false,
            hasPromptedPermissions = preferences[KEY_PERMISSION_PROMPTED] ?: false,
            authUserId = preferences[KEY_SUPABASE_AUTH_USER_ID] ?: ""
        )
    }

    suspend fun setPermissionsPrompted(prompted: Boolean = true) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PERMISSION_PROMPTED] = prompted
        }
    }

    suspend fun setLocationSharing(enabled: Boolean, userExplicit: Boolean = true) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOCATION_SHARING] = enabled
            if (userExplicit) {
                prefs[KEY_LOCATION_SHARING_USER_SET] = true
            }
        }
    }

    suspend fun saveSession(
        userName: String,
        coupleId: String,
        coupleCode: String,
        username: String = "",
        authUserId: String = ""
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_NAME] = userName
            if (username.isNotBlank()) {
                prefs[KEY_USERNAME] = username
            }
            if (authUserId.isNotBlank()) {
                prefs[KEY_SUPABASE_AUTH_USER_ID] = authUserId
            }
            prefs[KEY_COUPLE_ID] = coupleId
            prefs[KEY_COUPLE_CODE] = coupleCode
            prefs[KEY_IS_PAIRED] = coupleId.isNotBlank()
        }
    }

    /**
     * Caches ONLY non-sensitive profile metadata retrieved after verified authentication.
     */
    suspend fun cacheNonSensitiveProfile(
        userName: String,
        email: String,
        coupleId: String,
        dateOfBirth: String,
        avatarUrl: String
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_NAME] = userName
            prefs[KEY_USER_EMAIL] = email
            prefs[KEY_COUPLE_ID] = coupleId
            prefs[KEY_DATE_OF_BIRTH] = dateOfBirth
            prefs[KEY_AVATAR_PATH] = avatarUrl
            prefs[KEY_IS_PAIRED] = coupleId.isNotBlank()
        }
    }

    suspend fun updateUserProfile(name: String, email: String, dob: String, avatarPath: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_NAME] = name
            prefs[KEY_USER_EMAIL] = email
            prefs[KEY_DATE_OF_BIRTH] = dob
            prefs[KEY_AVATAR_PATH] = avatarPath
        }
    }

    suspend fun updatePartnerProfile(name: String, email: String, dob: String, avatarUrl: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PARTNER_NAME] = name
            prefs[KEY_PARTNER_EMAIL] = email
            prefs[KEY_PARTNER_DOB] = dob
            prefs[KEY_PARTNER_AVATAR_URL] = avatarUrl
        }
    }

    suspend fun updateRelationshipStartDate(date: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOVE_START_DATE] = date
        }
    }

    suspend fun updateMonthlyBudget(budget: Double) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MONTHLY_BUDGET] = budget
        }
    }

    suspend fun updateSavingsGoals(json: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SAVINGS_GOALS] = json
        }
    }

    suspend fun updateRecurringBills(json: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RECURRING_BILLS] = json
        }
    }

    suspend fun updateMovieWatchlist(json: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MOVIE_WATCHLIST] = json
        }
    }

    suspend fun updateAppPin(pin: String) {
        context.dataStore.edit { prefs ->
            if (pin.isBlank()) {
                prefs.remove(KEY_APP_PIN)
            } else {
                prefs[KEY_APP_PIN] = pin
            }
        }
    }

    suspend fun updateScreenSecurity(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SCREEN_SECURITY] = enabled
        }
    }

    suspend fun clearSession(clearProfileDetails: Boolean = true) {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_COUPLE_ID)
            prefs.remove(KEY_COUPLE_CODE)
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_SUPABASE_AUTH_USER_ID)
            prefs[KEY_IS_PAIRED] = false
            prefs[KEY_LOCATION_SHARING] = false
            prefs.remove(KEY_LOCATION_SHARING_USER_SET)
            if (clearProfileDetails) {
                prefs.remove(KEY_USER_NAME)
                prefs.remove(KEY_USER_EMAIL)
                prefs.remove(KEY_DATE_OF_BIRTH)
                prefs.remove(KEY_AVATAR_PATH)
                prefs.remove(KEY_PARTNER_NAME)
                prefs.remove(KEY_PARTNER_EMAIL)
                prefs.remove(KEY_PARTNER_DOB)
                prefs.remove(KEY_PARTNER_AVATAR_URL)
                prefs.remove(KEY_APP_PIN)
                prefs.remove(KEY_MOVIE_WATCHLIST)
            }
        }
    }
}
