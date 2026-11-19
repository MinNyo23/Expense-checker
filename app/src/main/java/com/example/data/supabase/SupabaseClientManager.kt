package com.example.data.supabase

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.AccountLinkCode
import com.example.data.model.AuthResult
import com.example.data.model.Couple
import com.example.data.model.CoupleMemo
import com.example.data.model.CoupleSyncCounts
import com.example.data.model.DeviceSession
import com.example.data.model.Expense
import com.example.data.model.LinkCodeResult
import com.example.data.model.LiveLocation
import com.example.data.model.LocationHistoryItem
import com.example.data.model.MoneyAccount
import com.example.data.model.SupabaseDomainError
import com.example.data.model.UserProfile
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

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
data class DohQueryResult(
    val status: Int = -1,
    val ips: List<String> = emptyList()
) {
    val isNxDomain: Boolean get() = status == 3
}

/**
 * Custom OkHttp Dns that:
 * 1. Uses System DNS + DoH (Cloudflare 1.1.1.1 & Google 8.8.8.8 via direct IPv4 literals).
 * 2. Gathers multiple edge IPs when system DNS returns only one or previously timed out.
 * 3. Reorders candidates by fast TCP port-443 reachability and deprioritizes timed-out IPs.
 */
class ResilientSupabaseDns : Dns {
    private val cache = ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()
    private val nxDomainHosts = ConcurrentHashMap<String, Long>()
    private val timedOutHosts = ConcurrentHashMap<String, Long>()
    private val failedIps = ConcurrentHashMap<String, MutableSet<String>>()

    private val dohClient = OkHttpClient.Builder()
        .dns(Dns.SYSTEM)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    override fun lookup(hostname: String): List<InetAddress> {
        val cleanHost = hostname.trim().lowercase()
        if (cleanHost.isBlank()) {
            throw UnknownHostException("Empty hostname")
        }

        // Skip DoH for IP literals or local names
        if (isIpLiteral(cleanHost) || cleanHost == "localhost" || !cleanHost.contains(".")) {
            return Dns.SYSTEM.lookup(cleanHost)
        }

        val isTimedOutHost = isRecentlyTimedOut(cleanHost)
        val cached = cache[cleanHost]
        if (!isTimedOutHost && cached != null && System.currentTimeMillis() - cached.first < CACHE_TTL_MS) {
            return cached.second
        }

        val candidates = LinkedHashMap<String, InetAddress>()

        // 1. Android System DNS
        try {
            for (addr in Dns.SYSTEM.lookup(cleanHost)) {
                val ip = addr.hostAddress ?: continue
                candidates.putIfAbsent(ip, addr)
            }
        } catch (systemErr: UnknownHostException) {
            Log.w("ResilientSupabaseDns", "System DNS failed for '$cleanHost': ${systemErr.message}, checking DoH fallback")
        }

        // 2. Also query Public DoH when system DNS failed, returned <= 1 IP for Supabase, or previous IP timed out
        val shouldEnrichWithDoh = candidates.isEmpty() ||
            isTimedOutHost ||
            (cleanHost.endsWith(".supabase.co") && candidates.size <= 1)

        var sawNxDomain = false
        if (shouldEnrichWithDoh) {
            val encodedHost = URLEncoder.encode(cleanHost, "UTF-8")
            val endpoints = listOf(
                "https://1.1.1.1/dns-query?name=$encodedHost&type=A",
                "https://8.8.8.8/resolve?name=$encodedHost&type=A",
                "https://1.0.0.1/dns-query?name=$encodedHost&type=A",
                "https://8.8.4.4/resolve?name=$encodedHost&type=A"
            )

            for (endpoint in endpoints) {
                val dohRes = queryDoh(endpoint) ?: continue
                if (dohRes.isNxDomain) {
                    sawNxDomain = true
                    continue
                }
                dohRes.ips.forEach { ip ->
                    val addr = runCatching { InetAddress.getByName(ip) }.getOrNull()
                    if (addr != null) {
                        candidates.putIfAbsent(addr.hostAddress ?: ip, addr)
                    }
                }
                if (candidates.size >= 3 && !isTimedOutHost) break
            }

            if (candidates.isEmpty() && !sawNxDomain) {
                for (endpoint in listOf(
                    "https://1.1.1.1/dns-query?name=$encodedHost&type=AAAA",
                    "https://8.8.8.8/resolve?name=$encodedHost&type=AAAA"
                )) {
                    val dohRes = queryDoh(endpoint) ?: continue
                    if (dohRes.isNxDomain) {
                        sawNxDomain = true
                        continue
                    }
                    dohRes.ips.forEach { ip ->
                        val addr = runCatching { InetAddress.getByName(ip) }.getOrNull()
                        if (addr != null) {
                            candidates.putIfAbsent(addr.hostAddress ?: ip, addr)
                        }
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            if (sawNxDomain) {
                nxDomainHosts[cleanHost] = System.currentTimeMillis()
            }
            throw UnknownHostException("Unable to resolve host \"$cleanHost\": No address associated with hostname")
        }

        nxDomainHosts.remove(cleanHost)
        val badSet = failedIps[cleanHost].orEmpty()
        val ordered = candidates.values
            .sortedWith(compareBy<InetAddress> { (it.hostAddress ?: "") in badSet })
            .toMutableList()

        // If we have multiple candidate IPs or experienced a timeout, probe port 443 to rank a working IP first
        if (ordered.size > 1 || isTimedOutHost) {
            val reachableFirst = reorderWithQuickTcpProbe(ordered)
            cache[cleanHost] = System.currentTimeMillis() to reachableFirst
            return reachableFirst
        }

        cache[cleanHost] = System.currentTimeMillis() to ordered
        return ordered
    }

    private fun reorderWithQuickTcpProbe(addresses: List<InetAddress>): List<InetAddress> {
        val reachable = mutableListOf<InetAddress>()
        val untestedOrFailed = mutableListOf<InetAddress>()
        addresses.forEachIndexed { index, addr ->
            if (index < 4 && canConnectTcp443(addr, timeoutMs = 1500)) {
                reachable.add(addr)
                if (reachable.isNotEmpty()) {
                    // Put the first confirmed reachable IP immediately up front, keep rest in order
                    untestedOrFailed.addAll(addresses.drop(index + 1))
                    return reachable + untestedOrFailed.filterNot { it in reachable }
                }
            } else {
                untestedOrFailed.add(addr)
            }
        }
        return if (reachable.isNotEmpty()) reachable + untestedOrFailed else addresses
    }

    private fun canConnectTcp443(address: InetAddress, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(address, 443), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun queryDoh(url: String): DohQueryResult? {
        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/dns-json")
                .get()
                .build()
            dohClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                parseDohResponse(body)
            }
        } catch (e: Exception) {
            Log.d("ResilientSupabaseDns", "DoH endpoint unavailable ($url): ${e.message}")
            null
        }
    }

    fun markHostTimedOut(host: String, failedIp: String? = null) {
        val clean = host.trim().lowercase()
        if (clean.isBlank()) return
        timedOutHosts[clean] = System.currentTimeMillis()
        cache.remove(clean)
        if (!failedIp.isNullOrBlank()) {
            val set = failedIps.computeIfAbsent(clean) {
                Collections.synchronizedSet(mutableSetOf())
            }
            set.add(failedIp.trim())
        }
    }

    fun wasNxDomain(host: String): Boolean {
        val ts = nxDomainHosts[host.trim().lowercase()] ?: return false
        return System.currentTimeMillis() - ts < CACHE_TTL_MS
    }

    private fun isRecentlyTimedOut(host: String): Boolean {
        val ts = timedOutHosts[host] ?: return false
        return System.currentTimeMillis() - ts < TIMEOUT_MEMORY_MS
    }

    fun clearCacheForHost(host: String) {
        val clean = host.trim().lowercase()
        cache.remove(clean)
        nxDomainHosts.remove(clean)
        timedOutHosts.remove(clean)
        failedIps.remove(clean)
    }

    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
        private const val TIMEOUT_MEMORY_MS = 2 * 60 * 1000L

        private val ipv4Regex = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

        fun isIpLiteral(host: String): Boolean = ipv4Regex.matches(host) || host.contains(":")

        fun parseDohResponse(body: String): DohQueryResult {
            val trimmed = body.trim()
            if (trimmed.isEmpty()) return DohQueryResult()
            return try {
                val root = Json.parseToJsonElement(trimmed).jsonObject
                val status = root["Status"]?.jsonPrimitive?.intOrNull ?: -1
                val ips = root["Answer"]?.jsonArray?.mapNotNull { el ->
                    val obj = el.jsonObject
                    val type = obj["type"]?.jsonPrimitive?.intOrNull
                    val data = obj["data"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if ((type == 1 || type == 28) && data.isNotBlank()) data else null
                } ?: emptyList()
                DohQueryResult(status = status, ips = ips)
            } catch (_: Exception) {
                DohQueryResult()
            }
        }
    }
}

class SupabaseClientManager(
    private val appContext: Context? = null,
    private val clientProvider: SupabaseClientProvider? = null
) {
    @PublishedApi
    internal val tag = "SupabaseClientManager"

    private val dnsResolver: ResilientSupabaseDns = clientProvider?.dnsResolver ?: ResilientSupabaseDns()

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @PublishedApi
    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val httpClient = OkHttpClient.Builder()
        .dns(dnsResolver)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    inline fun <reified T> parseResponseList(body: String): List<T> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()
        return try {
            when {
                trimmed.startsWith("[") -> json.decodeFromString<List<T>>(trimmed)
                trimmed.startsWith("{") -> listOf(json.decodeFromString<T>(trimmed))
                else -> emptyList()
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to parse JSON response: ${e.message}")
            emptyList()
        }
    }

    // Realtime events flow for instant cross-device updates
    private val _realtimeExpenseEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val realtimeExpenseEvents: SharedFlow<String> = _realtimeExpenseEvents.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var pollingJob: Job? = null
    private val scopeErrorHandler = CoroutineExceptionHandler { _, throwable ->
        Log.w(tag, "SupabaseClientManager background coroutine error: ${throwable.message}")
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + scopeErrorHandler)

    fun hasAuthenticatedJwt(): Boolean =
        !clientProvider?.auth?.currentAccessTokenOrNull().isNullOrBlank()

    fun getAuthorizationBearer(): String {
        val jwt = clientProvider?.auth?.currentAccessTokenOrNull()?.trim().orEmpty()
        if (jwt.isBlank()) {
            throw SupabaseDomainError.AuthenticationRequired()
        }
        return jwt
    }

    private fun executeAuthenticatedRequest(buildRequest: () -> Request): Response {
        // Ensures authenticated JWT exists before performing any protected REST call.
        getAuthorizationBearer()
        return executeWithNetworkFallback(buildRequest())
    }

    fun getDefaultBuildUrl(): String {
        return if (BuildConfig.SUPABASE_URL.isNotBlank() && !BuildConfig.SUPABASE_URL.contains("your-project")) {
            normalizeSupabaseUrl(BuildConfig.SUPABASE_URL)
        } else {
            ""
        }
    }

    fun getSupabaseUrl(): String {
        if (clientProvider != null) {
            return clientProvider.supabaseUrl.ifBlank { "https://your-project.supabase.co" }
        }
        return if (BuildConfig.SUPABASE_URL.isNotBlank() && !BuildConfig.SUPABASE_URL.contains("your-project")) {
            normalizeSupabaseUrl(BuildConfig.SUPABASE_URL)
        } else {
            "https://your-project.supabase.co"
        }
    }

    fun getSupabaseKey(): String {
        if (clientProvider != null) {
            return clientProvider.anonKey
        }
        return if (BuildConfig.SUPABASE_ANON_KEY.isNotBlank() && !BuildConfig.SUPABASE_ANON_KEY.contains("your-anon-key")) {
            BuildConfig.SUPABASE_ANON_KEY.trim()
        } else {
            ""
        }
    }

    fun getActiveHost(): String {
        return extractHost(getSupabaseUrl())
    }

    fun isConfigured(): Boolean {
        if (clientProvider != null) {
            return clientProvider.isConfigured
        }
        val url = getSupabaseUrl()
        val key = getSupabaseKey()
        return url.startsWith("https://") && !url.contains("your-project") && key.length > 20
    }

    private fun collectErrorChain(e: Throwable): List<Throwable> {
        val visited = LinkedHashSet<Throwable>()
        val queue = ArrayDeque<Throwable>()
        queue.add(e)
        while (queue.isNotEmpty() && visited.size < 12) {
            val cur = queue.removeFirst()
            if (visited.add(cur)) {
                cur.cause?.let { queue.add(it) }
                cur.suppressed.forEach { queue.add(it) }
            }
        }
        return visited.toList()
    }

    private fun isConnectivityFailure(e: Throwable): Boolean {
        val chain = collectErrorChain(e)
        return chain.any { t ->
            t is UnknownHostException ||
                t is SocketTimeoutException ||
                t is ConnectException ||
                t.message?.contains("failed to connect", ignoreCase = true) == true ||
                t.message?.contains("timed out", ignoreCase = true) == true ||
                t.message?.contains("Unable to resolve host", ignoreCase = true) == true
        }
    }

    /**
     * Executes an HTTP request with automatic alternate-network (Cellular/VPN) and
     * alternate DoH edge IP retry when the active Wi-Fi or ISP route times out on port 443.
     */
    private fun executeWithNetworkFallback(request: Request): Response {
        var primaryError: Exception? = null
        try {
            return httpClient.newCall(request).execute()
        } catch (e: Exception) {
            primaryError = e
            if (!isConnectivityFailure(e)) throw e
        }

        val host = request.url.host.ifBlank { getActiveHost() }
        val failedIp = extractIpFromException(primaryError)
        dnsResolver.markHostTimedOut(host, failedIp)
        Log.w(tag, "Primary connection failed ($failedIp): ${primaryError.message}. Trying alternate network/IP...")

        // 1. Try alternate OS network (e.g. Cellular 4G/4.5G or VPN if Wi-Fi is blocking Cloudflare port 443)
        val context = appContext
        if (context != null) {
            runCatching {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val activeNet = cm?.activeNetwork
                val alternateNetworks = cm?.allNetworks.orEmpty().filter { net ->
                    if (net == activeNet) return@filter false
                    val caps = cm?.getNetworkCapabilities(net) ?: return@filter false
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
                }
                for (altNet in alternateNetworks) {
                    try {
                        val altClient = OkHttpClient.Builder()
                            .socketFactory(altNet.socketFactory)
                            .dns(object : Dns {
                                override fun lookup(hostname: String): List<InetAddress> {
                                    return runCatching { altNet.getAllByName(hostname).toList() }
                                        .getOrElse { dnsResolver.lookup(hostname) }
                                }
                            })
                            .connectTimeout(8, TimeUnit.SECONDS)
                            .readTimeout(20, TimeUnit.SECONDS)
                            .writeTimeout(20, TimeUnit.SECONDS)
                            .build()
                        val altResp = altClient.newCall(request).execute()
                        Log.i(tag, "Connected via alternate cellular/VPN network!")
                        return altResp
                    } catch (altErr: Exception) {
                        Log.w(tag, "Alternate network attempt failed: ${altErr.message}")
                    }
                }
            }
        }

        // 2. Retry once with DoH-refreshed / port-443-probed candidate IPs (deprioritizing the timed-out IP)
        return try {
            httpClient.newCall(request).execute()
        } catch (retryErr: Exception) {
            throw primaryError
        }
    }

    fun formatNetworkException(e: Throwable): IOException {
        val host = getActiveHost().ifBlank { "supabase.co" }
        val chain = collectErrorChain(e)
        val allMessages = chain.mapNotNull { it.message }.joinToString(" | ")
        val failedIp = extractIpFromException(e)

        val isUnknownHost = chain.any { it is UnknownHostException } ||
            allMessages.contains("Unable to resolve host", ignoreCase = true) ||
            allMessages.contains("No address associated with hostname", ignoreCase = true)

        val isTimeout = chain.any { it is SocketTimeoutException } ||
            allMessages.contains("failed to connect", ignoreCase = true) ||
            allMessages.contains("timed out", ignoreCase = true) ||
            allMessages.contains("ETIMEDOUT", ignoreCase = true) ||
            (allMessages.contains("after ", ignoreCase = true) && allMessages.contains("ms", ignoreCase = true))

        val isRefused = chain.any { it is ConnectException } ||
            allMessages.contains("ECONNREFUSED", ignoreCase = true) ||
            allMessages.contains("EHOSTUNREACH", ignoreCase = true)

        val isSsl = chain.any { it is SSLHandshakeException }

        return when {
            isUnknownHost -> {
                if (dnsResolver.wasNxDomain(host)) {
                    IOException(
                        "Supabase host \"$host\" does not exist in global DNS (NXDOMAIN). " +
                            "Your Supabase project may be deleted, paused, or mistyped — update URL & Key in Settings (gear icon) or AI Studio Secrets.",
                        e
                    )
                } else {
                    IOException(
                        "Cannot reach \"$host\" (Device DNS & 1.1.1.1/8.8.8.8 DNS failed). " +
                            "Check mobile data/Wi-Fi or verify your Supabase URL in Settings.",
                        e
                    )
                }
            }
            isTimeout -> {
                val ipHint = if (failedIp.isNotBlank()) " via $failedIp" else ""
                IOException(
                    "Connection to \"$host\" timed out on port 443$ipHint. " +
                        "Your Wi-Fi or ISP may be blocking Supabase/Cloudflare. Try turning Wi-Fi off (use 4G), enabling a VPN, or setting a custom URL/proxy in Settings.",
                    e
                )
            }
            isRefused -> IOException(
                "Connection refused by \"$host\". Verify your internet/VPN and Supabase Project URL in Settings.",
                e
            )
            isSsl -> IOException(
                "SSL/TLS handshake failed for \"$host\". Verify your device date/time and Supabase URL.",
                e
            )
            e is IOException -> e
            else -> IOException(e.message ?: "Network error contacting \"$host\"", e)
        }
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(
                IllegalStateException("Supabase configuration is not set yet.")
            )
        }
        val host = getActiveHost()
        dnsResolver.clearCacheForHost(host)
        try {
            val request = Request.Builder()
                .url("${getSupabaseUrl()}/auth/v1/health")
                .addHeader("apikey", getSupabaseKey())
                .get()
                .build()

            executeWithNetworkFallback(request).use { response ->
                when {
                    response.code in 200..299 || response.code == 404 ->
                        Result.success("Connected securely to $host")
                    response.code == 401 || response.code == 403 ->
                        Result.failure(IOException("Reached $host, but publishable key was rejected."))
                    else ->
                        Result.failure(IOException("Reached $host, server returned status ${response.code}."))
                }
            }
        } catch (e: Exception) {
            Result.failure(formatNetworkException(e))
        }
    }

    companion object {
        private val ipv4ExtractRegex = Regex("""\b(\d{1,3}(?:\.\d{1,3}){3})\b""")

        fun extractIpFromException(e: Throwable?): String {
            var cur = e
            var depth = 0
            while (cur != null && depth < 6) {
                val msg = cur.message.orEmpty()
                val match = ipv4ExtractRegex.find(msg)
                if (match != null) {
                    val ip = match.groupValues[1]
                    if (!ip.startsWith("192.168.") && !ip.startsWith("10.") && !ip.startsWith("127.")) {
                        return ip
                    }
                }
                cur = cur.cause
                depth++
            }
            return ""
        }

        fun normalizeSupabaseUrl(raw: String): String {
            val compact = raw.trim().replace(Regex("\\s+"), "")
            if (compact.isBlank()) return ""
            val withScheme = when {
                compact.startsWith("https://", ignoreCase = true) -> "https://" + compact.substring(8)
                compact.startsWith("http://", ignoreCase = true) -> "http://" + compact.substring(7)
                else -> "https://$compact"
            }
            val fixedTypo = withScheme
                .replace(".supabse.co", ".supabase.co", ignoreCase = true)
                .replace(".supbase.co", ".supabase.co", ignoreCase = true)
                .replace(".suapbase.co", ".supabase.co", ignoreCase = true)
            return fixedTypo.trimEnd('/')
        }

        fun extractHost(url: String): String {
            if (url.isBlank()) return ""
            return runCatching { URI(normalizeSupabaseUrl(url)).host.orEmpty() }
                .getOrDefault("")
        }
    }

    // --- Postgrest HTTP API Operations ---

    suspend fun getCoupleByCode(code: String): Result<Couple?> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success(null)
        }

        try {
            val url = "${getSupabaseUrl()}/rest/v1/couples?couple_code=eq.${code.trim().uppercase()}&select=*"
            executeAuthenticatedRequest {
                Request.Builder()
                    .url(url)
                    .addHeader("apikey", getSupabaseKey())
                    .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                    .get()
                    .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Supabase error: ${response.code} ${response.message}"))
                }
                val body = response.body?.string().orEmpty()
                val list = parseResponseList<Couple>(body)
                Result.success(list.firstOrNull())
            }
        } catch (e: Exception) {
            Log.w(tag, "Error fetching couple by code: ${e.message}")
            Result.failure(formatNetworkException(e))
        }
    }

    suspend fun createCouple(coupleCode: String): Result<Couple> = withContext(Dispatchers.IO) {
        val code = coupleCode.trim().uppercase()
        val localCouple = Couple(
            id = UUID.randomUUID().toString(),
            couple_code = code,
            created_at = SupabaseRepository.getTodayIso()
        )

        if (!isConfigured()) {
            return@withContext Result.success(localCouple)
        }

        var currentCode = code
        var attempts = 0
        val maxAttempts = 3

        while (attempts < maxAttempts) {
            attempts++
            val currentId = UUID.randomUUID().toString()
            try {
                val url = "${getSupabaseUrl()}/rest/v1/couples"
                // Include explicit ID to satisfy schemas regardless of default generator availability
                val jsonBody = """{"id":"$currentId","couple_code":"$currentCode"}"""
                val callResult: Result<Couple>? = executeAuthenticatedRequest {
                    Request.Builder()
                        .url(url)
                        .addHeader("apikey", getSupabaseKey())
                        .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=representation")
                        .post(jsonBody.toRequestBody("application/json".toMediaType()))
                        .build()
                }.use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        if (response.code == 409 && attempts < maxAttempts) {
                            Log.w(tag, "Couple code $currentCode already exists in Supabase (409), retrying with a fresh code...")
                            currentCode = SupabaseRepository.generateCoupleCode()
                            return@use null
                        }
                        val friendlyMsg = when (response.code) {
                            401 -> "401 Unauthorized: Session expired or invalid Supabase Anon Key. Please sign in again."
                            404 -> "404 Not Found: 'couples' table was not found in your Supabase database. Please run the SQL schema script in Supabase SQL Editor."
                            403, 42501 -> "Permission Denied: Please check Row Level Security (RLS) policies in Supabase."
                            409 -> "Couple code already exists. Please try again."
                            else -> "Supabase Error ${response.code}: $body"
                        }
                        return@withContext Result.failure(IOException(friendlyMsg))
                    }
                    val list = parseResponseList<Couple>(body)
                    val created = list.firstOrNull() ?: Couple(
                        id = currentId,
                        couple_code = currentCode,
                        created_at = SupabaseRepository.getTodayIso()
                    )
                    Result.success(created)
                }

                if (callResult != null) {
                    return@withContext callResult
                }
            } catch (e: Exception) {
                Log.w(tag, "Supabase connection error while inserting couple: ${e.message}")
                return@withContext Result.failure(formatNetworkException(e))
            }
        }

        Result.failure(IOException("Failed to generate a unique couple code after $maxAttempts attempts."))
    }

    suspend fun getExpenses(coupleId: String): Result<List<Expense>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success(emptyList())
        }

        try {
            val url = "${getSupabaseUrl()}/rest/v1/expenses?couple_id=eq.$coupleId&order=date.desc,created_at.desc&select=*"
            executeAuthenticatedRequest {
                Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .get()
                .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()
                    return@withContext Result.failure(IOException("Failed to fetch expenses: ${response.code} $err"))
                }
                val body = response.body?.string().orEmpty()
                val expenses = parseResponseList<Expense>(body)
                Result.success(expenses)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error fetching expenses: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getExpensesAfter(coupleId: String, createdAtExclusive: String): Result<List<Expense>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext Result.success(emptyList())
        try {
            val encodedTs = URLEncoder.encode(createdAtExclusive, "UTF-8")
            val url = "${getSupabaseUrl()}/rest/v1/expenses?couple_id=eq.$coupleId&created_at=gt.$encodedTs&order=date.desc,created_at.desc&select=*"
            executeAuthenticatedRequest {
                Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .get()
                .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()
                    return@withContext Result.failure(IOException("Failed to fetch incremental expenses: ${response.code} $err"))
                }
                val body = response.body?.string().orEmpty()
                Result.success(parseResponseList<Expense>(body))
            }
        } catch (e: Exception) {
            Log.w(tag, "Error fetching incremental expenses: ${e.message}")
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
            executeAuthenticatedRequest {
                Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()
                    return@withContext Result.failure(IOException("Failed to insert expense: ${response.code} $err"))
                }
                val body = response.body?.string().orEmpty()
                val list = parseResponseList<Expense>(body)
                val created = list.firstOrNull() ?: expense
                Result.success(created)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error inserting expense: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteExpense(expenseId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success(true)
        }

        try {
            val url = "${getSupabaseUrl()}/rest/v1/expenses?id=eq.$expenseId"
            executeAuthenticatedRequest {
                Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .delete()
                .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Failed to delete expense: ${response.code}"))
                }
                Result.success(true)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error deleting expense", e)
            Result.failure(e)
        }
    }

    // --- Profile Operations ---

    suspend fun getProfiles(coupleId: String): Result<List<UserProfile>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success(emptyList())
        }
        try {
            val url = "${getSupabaseUrl()}/rest/v1/profiles?couple_id=eq.$coupleId&select=*"
            executeAuthenticatedRequest {
                Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .get()
                .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()
                    return@withContext Result.failure(IOException("Failed to fetch profiles: ${response.code} $err"))
                }
                val body = response.body?.string().orEmpty()
                val profiles = parseResponseList<UserProfile>(body)
                Result.success(profiles)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error fetching profiles: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun upsertProfile(profile: UserProfile): Result<UserProfile> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success(profile)
        }
        try {
            val url = "${getSupabaseUrl()}/rest/v1/profiles"
            val profileWithId = if (profile.id.isBlank()) profile.copy(id = UUID.randomUUID().toString()) else profile
            val jsonBody = json.encodeToString(profileWithId)
            executeAuthenticatedRequest {
                Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()
                    return@withContext Result.failure(IOException("Failed to save profile: ${response.code} $err"))
                }
                val body = response.body?.string().orEmpty()
                val list = parseResponseList<UserProfile>(body)
                val updated = list.firstOrNull() ?: profileWithId
                Result.success(updated)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error upserting profile: ${e.message}")
            Result.failure(e)
        }
    }

    // --- Couple Memos & Photo Memories Endpoints ---

    suspend fun getMemos(coupleId: String): Result<List<CoupleMemo>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success(emptyList())
        }

        try {
            val url = "${getSupabaseUrl()}/rest/v1/memos?couple_id=eq.$coupleId&order=date.desc,created_at.desc&select=*"
            executeAuthenticatedRequest {
                Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .get()
                .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()
                    return@withContext Result.failure(IOException("Failed to fetch memos: ${response.code} $err"))
                }
                val body = response.body?.string().orEmpty()
                val list = parseResponseList<CoupleMemo>(body)
                Result.success(list)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error fetching memos from Supabase: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getMemosAfter(coupleId: String, createdAtExclusive: String): Result<List<CoupleMemo>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext Result.success(emptyList())
        try {
            val encodedTs = URLEncoder.encode(createdAtExclusive, "UTF-8")
            val url = "${getSupabaseUrl()}/rest/v1/memos?couple_id=eq.$coupleId&created_at=gt.$encodedTs&order=date.desc,created_at.desc&select=*"
            executeAuthenticatedRequest {
                Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .get()
                .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()
                    return@withContext Result.failure(IOException("Failed to fetch incremental memos: ${response.code} $err"))
                }
                val body = response.body?.string().orEmpty()
                Result.success(parseResponseList<CoupleMemo>(body))
            }
        } catch (e: Exception) {
            Log.w(tag, "Error fetching incremental memos: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun insertMemo(memo: CoupleMemo): Result<CoupleMemo> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success(memo)
        }

        try {
            val url = "${getSupabaseUrl()}/rest/v1/memos"
            val memoWithId = if (memo.id.isBlank()) memo.copy(id = UUID.randomUUID().toString()) else memo
            val jsonBody = json.encodeToString(memoWithId)

            executeAuthenticatedRequest {
                Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()
            }.use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Failed to insert memo: ${response.code} $body"))
                }
                val list = parseResponseList<CoupleMemo>(body)
                val created = list.firstOrNull() ?: memoWithId
                Result.success(created)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error inserting memo to Supabase: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteMemo(memoId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success(true)
        }

        try {
            val url = "${getSupabaseUrl()}/rest/v1/memos?id=eq.$memoId"
            executeAuthenticatedRequest {
                Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .delete()
                .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()
                    return@withContext Result.failure(IOException("Failed to delete memo: ${response.code} $err"))
                }
                Result.success(true)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error deleting memo: ${e.message}")
            Result.failure(e)
        }
    }

    // --- Live Location & Location History Operations ---

    suspend fun getLiveLocations(coupleId: String): Result<List<LiveLocation>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success(emptyList())
        }
        try {
            val url = "${getSupabaseUrl()}/rest/v1/live_locations?couple_id=eq.$coupleId&select=*"
            executeAuthenticatedRequest {
                Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .get()
                .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()
                    return@withContext Result.failure(IOException("Failed to fetch live locations: ${response.code} $err"))
                }
                val body = response.body?.string().orEmpty()
                val list = parseResponseList<LiveLocation>(body)
                Result.success(list)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error fetching live locations: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun upsertLiveLocation(location: LiveLocation): Result<LiveLocation> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success(location)
        }
        try {
            val url = "${getSupabaseUrl()}/rest/v1/live_locations"
            val locWithId = if (location.id.isBlank()) location.copy(id = UUID.randomUUID().toString()) else location
            val jsonBody = json.encodeToString(locWithId)

            executeAuthenticatedRequest {
                Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()
            }.use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Failed to upsert live location: ${response.code} $body"))
                }
                val list = parseResponseList<LiveLocation>(body)
                val updated = list.firstOrNull() ?: locWithId
                Result.success(updated)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error upserting live location: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getLocationHistory(coupleId: String, limit: Int = 100): Result<List<LocationHistoryItem>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success(emptyList())
        }
        try {
            val url = "${getSupabaseUrl()}/rest/v1/location_history?couple_id=eq.$coupleId&order=recorded_at.desc&limit=$limit&select=*"
            executeAuthenticatedRequest {
                Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .get()
                .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()
                    return@withContext Result.failure(IOException("Failed to fetch location history: ${response.code} $err"))
                }
                val body = response.body?.string().orEmpty()
                val list = parseResponseList<LocationHistoryItem>(body)
                Result.success(list)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error fetching location history: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getLocationHistoryAfter(
        coupleId: String,
        recordedAtExclusive: Long,
        limit: Int = 100
    ): Result<List<LocationHistoryItem>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext Result.success(emptyList())
        try {
            val url = "${getSupabaseUrl()}/rest/v1/location_history?couple_id=eq.$coupleId&recorded_at=gt.$recordedAtExclusive&order=recorded_at.desc&limit=$limit&select=*"
            executeAuthenticatedRequest {
                Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .get()
                .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()
                    return@withContext Result.failure(IOException("Failed to fetch incremental location history: ${response.code} $err"))
                }
                val body = response.body?.string().orEmpty()
                Result.success(parseResponseList<LocationHistoryItem>(body))
            }
        } catch (e: Exception) {
            Log.w(tag, "Error fetching incremental location history: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun insertLocationHistory(item: LocationHistoryItem): Result<LocationHistoryItem> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success(item)
        }
        try {
            val url = "${getSupabaseUrl()}/rest/v1/location_history"
            val itemWithId = if (item.id.isBlank()) item.copy(id = UUID.randomUUID().toString()) else item
            val jsonBody = json.encodeToString(itemWithId)

            executeAuthenticatedRequest {
                Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()
            }.use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Failed to insert location history: ${response.code} $body"))
                }
                val list = parseResponseList<LocationHistoryItem>(body)
                val created = list.firstOrNull() ?: itemWithId
                Result.success(created)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error inserting location history: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun clearLocationHistory(coupleId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success(true)
        }
        try {
            val url = "${getSupabaseUrl()}/rest/v1/location_history?couple_id=eq.$coupleId"
            executeAuthenticatedRequest {
                Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .delete()
                .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()
                    return@withContext Result.failure(IOException("Failed to clear location history: ${response.code} $err"))
                }
                Result.success(true)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error clearing location history: ${e.message}")
            Result.failure(e)
        }
    }

    // --- Separated Per-Owner Money Accounts (Cash / Multi-Bank / Card) Operations ---

    suspend fun getMoneyAccounts(coupleId: String): Result<List<MoneyAccount>> = withContext(Dispatchers.IO) {
        if (!isConfigured() || coupleId.isBlank()) {
            return@withContext Result.success(emptyList())
        }
        try {
            val url = "${getSupabaseUrl()}/rest/v1/money_accounts?couple_id=eq.$coupleId&order=updated_at.desc&select=*"
            executeAuthenticatedRequest {
                Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .get()
                .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()
                    return@withContext Result.failure(IOException("Failed to fetch money accounts: ${response.code} $err"))
                }
                val body = response.body?.string().orEmpty()
                Result.success(parseResponseList<MoneyAccount>(body))
            }
        } catch (e: Exception) {
            Log.w(tag, "Error fetching money accounts: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun upsertMoneyAccount(account: MoneyAccount): Result<MoneyAccount> = withContext(Dispatchers.IO) {
        val withId = if (account.id.isBlank()) account.copy(id = UUID.randomUUID().toString()) else account
        if (!isConfigured()) {
            return@withContext Result.success(withId)
        }
        try {
            val url = "${getSupabaseUrl()}/rest/v1/money_accounts"
            val jsonBody = json.encodeToString(withId)

            executeAuthenticatedRequest {
                Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()
            }.use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Failed to save money account: ${response.code} $body"))
                }
                val list = parseResponseList<MoneyAccount>(body)
                Result.success(list.firstOrNull() ?: withId)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error saving money account: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteMoneyAccount(accountId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isConfigured() || accountId.isBlank()) {
            return@withContext Result.success(true)
        }
        try {
            val url = "${getSupabaseUrl()}/rest/v1/money_accounts?id=eq.$accountId"
            executeAuthenticatedRequest {
                Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .delete()
                .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()
                    return@withContext Result.failure(IOException("Failed to delete money account: ${response.code} $err"))
                }
                Result.success(true)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error deleting money account: ${e.message}")
            Result.failure(e)
        }
    }

    // --- Persistent Device Login Session Operations ---

    suspend fun getDeviceSessionByDeviceId(deviceId: String): Result<DeviceSession?> = withContext(Dispatchers.IO) {
        if (!isConfigured() || deviceId.isBlank()) {
            return@withContext Result.success(null)
        }
        try {
            val url = "${getSupabaseUrl()}/rest/v1/device_sessions?device_id=eq.$deviceId&is_active=eq.true&select=*"
            executeAuthenticatedRequest {
                Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .get()
                .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()
                    return@withContext Result.failure(IOException("Failed to fetch device session: ${response.code} $err"))
                }
                val body = response.body?.string().orEmpty()
                val list = parseResponseList<DeviceSession>(body)
                Result.success(list.firstOrNull())
            }
        } catch (e: Exception) {
            Log.w(tag, "Error fetching device session: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getDeviceSessionsByCouple(coupleId: String): Result<List<DeviceSession>> = withContext(Dispatchers.IO) {
        if (!isConfigured() || coupleId.isBlank()) {
            return@withContext Result.success(emptyList())
        }
        try {
            val url = "${getSupabaseUrl()}/rest/v1/device_sessions?couple_id=eq.$coupleId&is_active=eq.true&order=last_login_at.desc&select=*"
            executeAuthenticatedRequest {
                Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .get()
                .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()
                    return@withContext Result.failure(IOException("Failed to fetch couple devices: ${response.code} $err"))
                }
                val body = response.body?.string().orEmpty()
                val list = parseResponseList<DeviceSession>(body)
                Result.success(list)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error fetching couple devices: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun upsertDeviceSession(deviceSession: DeviceSession): Result<DeviceSession> = withContext(Dispatchers.IO) {
        val deterministicId = if (deviceSession.id.isBlank()) {
            UUID.nameUUIDFromBytes(deviceSession.device_id.toByteArray()).toString()
        } else {
            deviceSession.id
        }
        val normalized = deviceSession.copy(id = deterministicId)

        if (!isConfigured()) {
            return@withContext Result.success(normalized)
        }

        try {
            val url = "${getSupabaseUrl()}/rest/v1/device_sessions?on_conflict=device_id"
            val jsonBody = json.encodeToString(normalized)

            executeAuthenticatedRequest {
                Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()
            }.use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Failed to save device session: ${response.code} $body"))
                }
                val list = parseResponseList<DeviceSession>(body)
                val saved = list.firstOrNull() ?: normalized
                Result.success(saved)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error saving device session: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun removeDeviceSession(deviceId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isConfigured() || deviceId.isBlank()) {
            return@withContext Result.success(true)
        }
        try {
            val url = "${getSupabaseUrl()}/rest/v1/device_sessions?device_id=eq.$deviceId"
            executeAuthenticatedRequest {
                Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .delete()
                .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()
                    return@withContext Result.failure(IOException("Failed to remove device session: ${response.code} $err"))
                }
                Result.success(true)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error removing device session: ${e.message}")
            Result.failure(e)
        }
    }

    // --- Lightweight Count & Latest-Timestamp Probe Operations (No Full Data Fetch Unless Changed) ---

    suspend fun getCoupleSyncCounts(coupleId: String): Result<CoupleSyncCounts> = withContext(Dispatchers.IO) {
        if (!isConfigured() || coupleId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("SUPABASE_NOT_CONFIGURED"))
        }

        // Try single-roundtrip RPC first (returns only 6 counts + 6 latest timestamps)
        try {
            val payload = buildJsonObject {
                put("p_couple_id", coupleId)
            }.toString()

            executeAuthenticatedRequest {
                Request.Builder()
                .url("${getSupabaseUrl()}/rest/v1/rpc/get_couple_sync_counts")
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            }.use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful && body.isNotBlank()) {
                    return@withContext Result.success(json.decodeFromString<CoupleSyncCounts>(body))
                }
                if (response.code != 404) {
                    Log.w(tag, "Sync counts RPC warning (${response.code}), falling back to REST count probes")
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Sync counts RPC failed (${e.message}), falling back to REST count probes")
        }

        // Fallback: parallel tiny PostgREST metadata probes (Prefer: count=exact, limit=1, timestamps only)
        fetchCoupleSyncCountsViaRest(coupleId)
    }

    private suspend fun fetchCoupleSyncCountsViaRest(coupleId: String): Result<CoupleSyncCounts> = try {
        coroutineScope {
            val expDeferred = async { probeTableMeta("expenses", "couple_id=eq.$coupleId", "created_at") }
            val memoDeferred = async { probeTableMeta("memos", "couple_id=eq.$coupleId", "created_at") }
            val profileDeferred = async { probeTableMeta("profiles", "couple_id=eq.$coupleId", "updated_at") }
            val liveLocDeferred = async { probeTableMeta("live_locations", "couple_id=eq.$coupleId", "updated_at") }
            val historyDeferred = async { probeTableMeta("location_history", "couple_id=eq.$coupleId", "recorded_at") }
            val devicesDeferred = async { probeTableMeta("device_sessions", "couple_id=eq.$coupleId&is_active=eq.true", "last_login_at") }
            val moneyDeferred = async { runCatching { probeTableMeta("money_accounts", "couple_id=eq.$coupleId", "updated_at") }.getOrNull() }

            val exp = expDeferred.await()
            val memo = memoDeferred.await()
            val profile = profileDeferred.await()
            val liveLoc = liveLocDeferred.await()
            val history = historyDeferred.await()
            val devices = devicesDeferred.await()
            val money = moneyDeferred.await()

            Result.success(
                CoupleSyncCounts(
                    expenses_count = exp.first,
                    expenses_latest = exp.second,
                    memos_count = memo.first,
                    memos_latest = memo.second,
                    profiles_count = profile.first,
                    profiles_latest = profile.second,
                    live_locations_count = liveLoc.first,
                    live_locations_latest = liveLoc.second.toLongOrNull() ?: 0L,
                    location_history_count = history.first,
                    location_history_latest = history.second.toLongOrNull() ?: 0L,
                    device_sessions_count = devices.first,
                    device_sessions_latest = devices.second,
                    money_accounts_count = money?.first,
                    money_accounts_latest = money?.second?.toLongOrNull()
                )
            )
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(tag, "REST count probe failed: ${e.message}")
        Result.failure(e)
    }

    private fun probeTableMeta(
        table: String,
        filterQuery: String,
        orderColumn: String
    ): Pair<Int, String> {
        val url = "${getSupabaseUrl()}/rest/v1/$table?$filterQuery&select=$orderColumn&order=$orderColumn.desc&limit=1"
        executeAuthenticatedRequest {
            Request.Builder()
                .url(url)
                .addHeader("apikey", getSupabaseKey())
                .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                .addHeader("Prefer", "count=exact")
                .get()
                .build()
        }.use { response ->
            if (!response.isSuccessful) {
                throw IOException("Probe failed for $table: ${response.code}")
            }
            val body = response.body?.string().orEmpty().trim()
            var latest = ""
            var parsedSize = 0
            if (body.startsWith("[")) {
                val arr = json.parseToJsonElement(body).jsonArray
                parsedSize = arr.size
                if (arr.isNotEmpty()) {
                    val obj: JsonObject = arr[0].jsonObject
                    val prim = obj[orderColumn]?.jsonPrimitive
                    latest = prim?.longOrNull?.toString() ?: prim?.contentOrNull.orEmpty()
                }
            }
            val count = parseCountFromContentRange(response.header("Content-Range"), parsedSize)
            return count to latest
        }
    }

    fun parseCountFromContentRange(contentRange: String?, fallbackCount: Int): Int {
        if (contentRange.isNullOrBlank()) return fallbackCount
        val totalPart = contentRange.substringAfter('/', "").trim()
        return totalPart.toIntOrNull() ?: fallbackCount
    }

    // --- Legacy custom user_accounts auth & OTP methods removed (handled via AuthRepository + Supabase Auth SDK) ---

    // --- Unique 6-Digit Account Linking Service Endpoints ---

    suspend fun isLinkCodeAvailable(code: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext true
        val cleanCode = code.trim().uppercase()
        try {
            val coupleUrl = "${getSupabaseUrl()}/rest/v1/couples?couple_code=eq.$cleanCode&select=id"
            executeAuthenticatedRequest {
                Request.Builder()
                    .url(coupleUrl)
                    .addHeader("apikey", getSupabaseKey())
                    .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                    .get()
                    .build()
            }.use { res ->
                if (res.isSuccessful && parseResponseList<Couple>(res.body?.string().orEmpty()).isNotEmpty()) {
                    return@withContext false
                }
            }

            val linkUrl = "${getSupabaseUrl()}/rest/v1/account_link_codes?link_code=eq.$cleanCode&select=id"
            executeAuthenticatedRequest {
                Request.Builder()
                    .url(linkUrl)
                    .addHeader("apikey", getSupabaseKey())
                    .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                    .get()
                    .build()
            }.use { res ->
                if (res.isSuccessful && parseResponseList<AccountLinkCode>(res.body?.string().orEmpty()).isNotEmpty()) {
                    return@withContext false
                }
            }
            true
        } catch (e: Exception) {
            Log.w(tag, "Code uniqueness lookup warning: ${e.message}")
            true
        }
    }

    suspend fun generateUniqueLinkCode(
        coupleId: String,
        email: String,
        userName: String
    ): Result<LinkCodeResult> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("SUPABASE_NOT_CONFIGURED"))
        }

        try {
            val payload = buildJsonObject {
                put("p_couple_id", coupleId)
                put("p_email", email.trim().lowercase())
                put("p_user_name", userName.trim())
            }.toString()

            executeAuthenticatedRequest {
                Request.Builder()
                    .url("${getSupabaseUrl()}/rest/v1/rpc/generate_unique_link_code")
                    .addHeader("apikey", getSupabaseKey())
                    .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
            }.use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val res = json.decodeFromString<LinkCodeResult>(body)
                    return@withContext if (res.success) {
                        _realtimeExpenseEvents.tryEmit("LINK_CODE_GENERATED:${res.link_code}")
                        Result.success(res)
                    } else {
                        Result.failure(IllegalArgumentException(res.message.ifBlank { "Failed to generate link code" }))
                    }
                }
                if (response.code == 404) {
                    return@withContext Result.failure(IllegalStateException("RPC_NOT_INSTALLED"))
                }
                Result.failure(IOException("Generate link code error (${response.code}): $body"))
            }
        } catch (e: Exception) {
            Log.w(tag, "Error calling generate_unique_link_code RPC: ${e.message}")
            Result.failure(formatNetworkException(e))
        }
    }

    suspend fun linkAccountsWithCode(
        email: String,
        userName: String,
        currentCoupleId: String,
        linkCode: String
    ): Result<AuthResult> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("SUPABASE_NOT_CONFIGURED"))
        }

        try {
            val payload = buildJsonObject {
                put("p_email", email.trim().lowercase())
                put("p_user_name", userName.trim())
                put("p_current_couple_id", currentCoupleId)
                put("p_link_code", linkCode.trim().uppercase())
            }.toString()

            executeAuthenticatedRequest {
                Request.Builder()
                    .url("${getSupabaseUrl()}/rest/v1/rpc/link_accounts_with_code")
                    .addHeader("apikey", getSupabaseKey())
                    .addHeader("Authorization", "Bearer ${getAuthorizationBearer()}")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
            }.use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val res = json.decodeFromString<AuthResult>(body)
                    return@withContext if (res.success) {
                        _realtimeExpenseEvents.tryEmit("ACCOUNTS_LINKED:${res.couple_id}")
                        Result.success(res)
                    } else {
                        Result.failure(IllegalArgumentException(res.message.ifBlank { "Invalid or expired link code" }))
                    }
                }
                if (response.code == 404) {
                    return@withContext Result.failure(IllegalStateException("RPC_NOT_INSTALLED"))
                }
                Result.failure(IOException("Link accounts error (${response.code}): $body"))
            }
        } catch (e: Exception) {
            Log.w(tag, "Error calling link_accounts_with_code RPC: ${e.message}")
            Result.failure(formatNetworkException(e))
        }
    }

    // --- Supabase Realtime WebSocket Connection ---

    fun startRealtime(coupleId: String) {
        if (!isConfigured() || coupleId.isBlank() || !hasAuthenticatedJwt()) {
            Log.d(tag, "Realtime idle: awaiting authenticated session")
            return
        }

        restartRealtimeConnection()

        val wsUrl = try {
            getSupabaseUrl()
                .replace("https://", "wss://")
                .replace("http://", "ws://") + "/realtime/v1/websocket?apikey=${getSupabaseKey()}&vsn=1.0.0"
        } catch (e: Exception) {
            Log.w(tag, "Invalid realtime URL: ${e.message}")
            return
        }

        val request = try {
            Request.Builder().url(wsUrl).build()
        } catch (e: Exception) {
            Log.w(tag, "Failed building WebSocket request for $wsUrl: ${e.message}")
            return
        }

        try {
            webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(tag, "Supabase Realtime WebSocket connected")
                    try {
                        val tables = listOf(
                            "expenses",
                            "profiles",
                            "memos",
                            "live_locations",
                            "device_sessions",
                            "location_history",
                            "account_link_codes",
                            "money_accounts"
                        )
                        val bearerToken = getAuthorizationBearer()
                        tables.forEachIndexed { idx, tableName ->
                            val joinMsg = """
                                {
                                  "topic": "realtime:public:$tableName",
                                  "event": "phx_join",
                                  "payload": {
                                    "access_token": "$bearerToken",
                                    "config": {
                                      "postgres_changes": [
                                        {"event": "*", "schema": "public", "table": "$tableName"}
                                      ]
                                    }
                                  },
                                  "ref": "${idx + 1}"
                                }
                            """.trimIndent()
                            webSocket.send(joinMsg)
                        }

                        // Start heartbeat
                        heartbeatJob?.cancel()
                        heartbeatJob = scope.launch {
                            var ref = tables.size + 1
                            while (isActive) {
                                delay(25_000)
                                val heartbeat = """{"topic":"phoenix","event":"heartbeat","payload":{},"ref":"$ref"}"""
                                val sent = runCatching { webSocket.send(heartbeat) }.getOrDefault(false)
                                if (!sent) break
                                ref++
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "Error joining realtime channels: ${e.message}")
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
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
        } catch (e: Exception) {
            Log.w(tag, "Unable to open WebSocket: ${e.message}")
        }

        // Safety polling every 25 seconds to guarantee synchronization without jank
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                delay(25_000)
                _realtimeExpenseEvents.tryEmit("POLL_SYNC")
            }
        }
    }

    private fun restartRealtimeConnection() {
        heartbeatJob?.cancel()
        pollingJob?.cancel()
        runCatching { webSocket?.close(1000, "Reconnecting") }
        webSocket = null
    }

    fun stopRealtime() {
        restartRealtimeConnection()
    }
}
