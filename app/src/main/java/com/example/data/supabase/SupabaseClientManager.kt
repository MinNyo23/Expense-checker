package com.example.data.supabase

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.Couple
import com.example.data.model.Expense
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Official Supabase Kotlin Client Documentation snippet:
 *
 * ```kotlin
 * import io.github.jan.supabase.createSupabaseClient
 * import io.github.jan.supabase.postgrest.Postgrest
 * import io.github.jan.supabase.postgrest.postgrest
 * import io.github.jan.supabase.realtime.Realtime
 * import io.github.jan.supabase.realtime.realtime
 * import io.github.jan.supabase.realtime.channel
 * import io.github.jan.supabase.realtime.postgresChangeFlow
 *
 * val supabase = createSupabaseClient(
 *     supabaseUrl = "https://your-project.supabase.co",
 *     supabaseKey = "your-anon-key"
 * ) {
 *     install(Postgrest)
 *     install(Realtime)
 * }
 * ```
 */
class SupabaseClientManager(
    private var customUrl: String = "",
    private var customKey: String = ""
) {
    private val tag = "SupabaseClientManager"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // Realtime events flow for instant cross-device updates
    private val _realtimeExpenseEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val realtimeExpenseEvents: SharedFlow<String> = _realtimeExpenseEvents.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var pollingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun updateCredentials(url: String, key: String) {
        customUrl = url.trim()
        customKey = key.trim()
        restartRealtimeConnection()
    }

    fun getSupabaseUrl(): String {
        return when {
            customUrl.isNotBlank() -> customUrl
            BuildConfig.SUPABASE_URL.isNotBlank() && !BuildConfig.SUPABASE_URL.contains("your-project") -> BuildConfig.SUPABASE_URL
            else -> "https://your-project.supabase.co"
        }
    }

    fun getSupabaseKey(): String {
        return when {
            customKey.isNotBlank() -> customKey
            BuildConfig.SUPABASE_ANON_KEY.isNotBlank() && !BuildConfig.SUPABASE_ANON_KEY.contains("your-anon-key") -> BuildConfig.SUPABASE_ANON_KEY
            else -> ""
        }
    }

    fun isConfigured(): Boolean {
        val url = getSupabaseUrl()
        val key = getSupabaseKey()
        return url.startsWith("https://") && !url.contains("your-project") && key.length > 20
    }

    // --- Postgrest HTTP API Operations ---

    suspend fun getCoupleByCode(code: String): Result<Couple?> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success(null)
        }

        try {
            val url = "${getSupabaseUrl()}/rest/v1/couples?couple_code=eq.${code.trim().uppercase()}&select=*"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getSupabaseKey()}")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Supabase error: ${response.code} ${response.message}"))
                }
                val body = response.body?.string().orEmpty()
                val list = json.decodeFromString<List<Couple>>(body)
                Result.success(list.firstOrNull())
            }
        } catch (e: Exception) {
            Log.e(tag, "Error fetching couple by code", e)
            Result.failure(e)
        }
    }

    suspend fun createCouple(coupleCode: String): Result<Couple> = withContext(Dispatchers.IO) {
        val newCouple = Couple(
            id = UUID.randomUUID().toString(),
            couple_code = coupleCode.trim().uppercase()
        )

        if (!isConfigured()) {
            return@withContext Result.success(newCouple)
        }

        try {
            val url = "${getSupabaseUrl()}/rest/v1/couples"
            val jsonBody = json.encodeToString(newCouple)
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getSupabaseKey()}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()
                    return@withContext Result.failure(IOException("Failed to create couple: ${response.code} $err"))
                }
                val body = response.body?.string().orEmpty()
                val created = json.decodeFromString<List<Couple>>(body).firstOrNull() ?: newCouple
                Result.success(created)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error inserting couple", e)
            Result.failure(e)
        }
    }

    suspend fun getExpenses(coupleId: String): Result<List<Expense>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success(emptyList())
        }

        try {
            val url = "${getSupabaseUrl()}/rest/v1/expenses?couple_id=eq.$coupleId&order=date.desc,created_at.desc&select=*"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getSupabaseKey()}")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()
                    return@withContext Result.failure(IOException("Failed to fetch expenses: ${response.code} $err"))
                }
                val body = response.body?.string().orEmpty()
                val expenses = json.decodeFromString<List<Expense>>(body)
                Result.success(expenses)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error fetching expenses", e)
            Result.failure(e)
        }
    }

    suspend fun insertExpense(expense: Expense): Result<Expense> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success(expense)
        }

        try {
            val url = "${getSupabaseUrl()}/rest/v1/expenses"
            val jsonBody = json.encodeToString(expense)
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getSupabaseKey()}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()
                    return@withContext Result.failure(IOException("Failed to insert expense: ${response.code} $err"))
                }
                val body = response.body?.string().orEmpty()
                val created = json.decodeFromString<List<Expense>>(body).firstOrNull() ?: expense
                // Trigger local realtime broadcast
                _realtimeExpenseEvents.tryEmit("INSERT:${created.id}")
                Result.success(created)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error inserting expense", e)
            Result.failure(e)
        }
    }

    suspend fun deleteExpense(expenseId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success(true)
        }

        try {
            val url = "${getSupabaseUrl()}/rest/v1/expenses?id=eq.$expenseId"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getSupabaseKey()}")
                .delete()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Failed to delete expense: ${response.code}"))
                }
                _realtimeExpenseEvents.tryEmit("DELETE:$expenseId")
                Result.success(true)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error deleting expense", e)
            Result.failure(e)
        }
    }

    // --- Supabase Realtime WebSocket Connection ---

    fun startRealtime(coupleId: String) {
        if (!isConfigured()) {
            Log.d(tag, "Supabase not configured, realtime websocket idle")
            return
        }

        restartRealtimeConnection()

        val wsUrl = getSupabaseUrl()
            .replace("https://", "wss://")
            .replace("http://", "ws://") + "/realtime/v1/websocket?apikey=${getSupabaseKey()}&vsn=1.0.0"

        val request = Request.Builder().url(wsUrl).build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(tag, "Supabase Realtime WebSocket connected")
                // Join public expenses channel
                val joinMessage = """
                    {
                      "topic": "realtime:public:expenses",
                      "event": "phx_join",
                      "payload": {
                        "config": {
                          "postgres_changes": [
                            {"event": "*", "schema": "public", "table": "expenses"}
                          ]
                        }
                      },
                      "ref": "1"
                    }
                """.trimIndent()
                webSocket.send(joinMessage)

                // Start heartbeat
                heartbeatJob?.cancel()
                heartbeatJob = scope.launch {
                    var ref = 2
                    while (isActive) {
                        delay(25_000)
                        val heartbeat = """{"topic":"phoenix","event":"heartbeat","payload":{},"ref":"$ref"}"""
                        webSocket.send(heartbeat)
                        ref++
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(tag, "Supabase Realtime message received: $text")
                if (text.contains("postgres_changes") || text.contains("INSERT") || text.contains("UPDATE") || text.contains("DELETE")) {
                    _realtimeExpenseEvents.tryEmit("EVENT:$text")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(tag, "Supabase Realtime WebSocket error: ${t.message}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "Supabase Realtime WebSocket closed: $reason")
            }
        })

        // Also setup safety polling every 8 seconds to guarantee synchronization
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                delay(8_000)
                _realtimeExpenseEvents.tryEmit("POLL_SYNC")
            }
        }
    }

    private fun restartRealtimeConnection() {
        heartbeatJob?.cancel()
        pollingJob?.cancel()
        webSocket?.close(1000, "Reconnecting")
        webSocket = null
    }

    fun stopRealtime() {
        restartRealtimeConnection()
    }
}
