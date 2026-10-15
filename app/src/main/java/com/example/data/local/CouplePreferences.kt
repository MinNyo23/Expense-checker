package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "couple_prefs")

data class UserSession(
    val userName: String = "",
    val coupleId: String = "",
    val coupleCode: String = "",
    val isPaired: Boolean = false,
    val customSupabaseUrl: String = "",
    val customSupabaseKey: String = ""
)

class CouplePreferences(private val context: Context) {

    companion object {
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_COUPLE_ID = stringPreferencesKey("couple_id")
        val KEY_COUPLE_CODE = stringPreferencesKey("couple_code")
        val KEY_IS_PAIRED = booleanPreferencesKey("is_paired")
        val KEY_SUPABASE_URL = stringPreferencesKey("supabase_url")
        val KEY_SUPABASE_KEY = stringPreferencesKey("supabase_key")
    }

    val userSessionFlow: Flow<UserSession> = context.dataStore.data.map { preferences ->
        UserSession(
            userName = preferences[KEY_USER_NAME] ?: "",
            coupleId = preferences[KEY_COUPLE_ID] ?: "",
            coupleCode = preferences[KEY_COUPLE_CODE] ?: "",
            isPaired = preferences[KEY_IS_PAIRED] ?: false,
            customSupabaseUrl = preferences[KEY_SUPABASE_URL] ?: "",
            customSupabaseKey = preferences[KEY_SUPABASE_KEY] ?: ""
        )
    }

    suspend fun saveSession(userName: String, coupleId: String, coupleCode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_NAME] = userName
            prefs[KEY_COUPLE_ID] = coupleId
            prefs[KEY_COUPLE_CODE] = coupleCode
            prefs[KEY_IS_PAIRED] = true
        }
    }

    suspend fun updateCustomSupabase(url: String, key: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SUPABASE_URL] = url
            prefs[KEY_SUPABASE_KEY] = key
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_COUPLE_ID)
            prefs.remove(KEY_COUPLE_CODE)
            prefs[KEY_IS_PAIRED] = false
        }
    }
}
