package com.example.data.supabase

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.SupabaseErrorMapper
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Request
import java.net.ConnectException
import java.net.URI
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import java.util.concurrent.TimeUnit

/**
 * Single reusable Supabase SDK client initializer reading SUPABASE_URL and the publishable/anon key
 * exclusively from BuildConfig (Secrets Gradle Plugin / .env).
 *
 * Security guarantees:
 * - Never embeds or accepts a service_role key.
 * - Never logs access tokens, refresh tokens, passwords, or keys.
 */
class SupabaseClientProvider(
    rawUrl: String = BuildConfig.SUPABASE_URL,
    rawAnonKey: String = BuildConfig.SUPABASE_ANON_KEY
) {
    private val tag = "SupabaseClientProvider"
    internal val dnsResolver = ResilientSupabaseDns()

    val supabaseUrl: String = normalizeSupabaseUrl(rawUrl)
    private val supabaseAnonKey: String = rawAnonKey.trim()
    internal val anonKey: String get() = supabaseAnonKey

    val host: String
        get() = runCatching { URI(supabaseUrl).host.orEmpty() }.getOrDefault("")

    val isConfigured: Boolean
        get() = supabaseUrl.startsWith("https://") &&
            !supabaseUrl.contains("your-project") &&
            supabaseAnonKey.isNotBlank() &&
            !supabaseAnonKey.contains("your-anon-key") &&
            !isLikelyServiceRoleKey(supabaseAnonKey)

    private val okHttpClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .dns(dnsResolver)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val client: SupabaseClient by lazy {
        check(!isLikelyServiceRoleKey(supabaseAnonKey)) {
            "Service-role keys must never be embedded in the mobile client."
        }
        createSupabaseClient(
            supabaseUrl = supabaseUrl.ifBlank { "https://placeholder.supabase.co" },
            supabaseKey = supabaseAnonKey.ifBlank { "placeholder-anon-key" }
        ) {
            httpEngine = Android.create {
                connectTimeout = 10_000
                socketTimeout = 20_000
            }
            install(Auth) {
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
                autoSaveToStorage = true
            }
            install(Postgrest)
            install(Storage)
        }
    }

    val auth get() = client.auth
    val postgrest get() = client.postgrest
    val storage get() = client.storage

    suspend fun testConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext false to "Supabase backend configuration is missing. Configure SUPABASE_URL and SUPABASE_ANON_KEY in Secrets."
        }
        try {
            val req = Request.Builder()
                .url("$supabaseUrl/auth/v1/health")
                .addHeader("apikey", supabaseAnonKey)
                .get()
                .build()
            okHttpClient.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    true to "Connected securely to $host"
                } else {
                    false to "Supabase responded with HTTP ${response.code}. Check the project URL, API key, and Auth/RLS configuration."
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Connection check failed for host '$host': ${e.javaClass.simpleName}")
            false to connectionDiagnosticMessage(e)
        }
    }

    private fun connectionDiagnosticMessage(error: Throwable): String {
        return when {
            error.findCause<UnknownHostException>() != null ->
                "DNS could not resolve $host. Try another ISP or Private DNS (dns.google / one.one.one.one); this may be a regional ISP block."
            error.findCause<SocketTimeoutException>() != null || error.findCause<ConnectException>() != null ->
                "Could not reach $host over HTTPS. Try another network or VPN, then contact your ISP if the problem continues."
            error.findCause<SSLHandshakeException>() != null ->
                "TLS validation failed while connecting to $host. Check the device date/time and network interception."
            else -> SupabaseErrorMapper.toUserMessage(error)
        }
    }

    private inline fun <reified T : Throwable> Throwable.findCause(): T? {
        var current: Throwable? = this
        while (current != null) {
            if (current is T) return current
            current = current.cause
        }
        return null
    }

    companion object {
        fun normalizeSupabaseUrl(raw: String): String {
            var cleaned = raw.trim()
            if (cleaned.isBlank()) return ""
            cleaned = cleaned.trimEnd('/')
            if (!cleaned.startsWith("http://", ignoreCase = true) &&
                !cleaned.startsWith("https://", ignoreCase = true)
            ) {
                cleaned = "https://$cleaned"
            }
            if (!cleaned.contains(".") && !cleaned.contains("localhost") && !cleaned.contains(":")) {
                cleaned = "$cleaned.supabase.co"
            }
            return cleaned
        }

        private fun isLikelyServiceRoleKey(key: String): Boolean {
            if (key.startsWith("sb_secret_", ignoreCase = true)) return true
            val parts = key.split(".")
            if (parts.size == 3) {
                val payload = runCatching {
                    String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
                }.getOrDefault("")
                if (payload.contains("\"role\":\"service_role\"", ignoreCase = true)) {
                    return true
                }
            }
            return false
        }
    }
}
