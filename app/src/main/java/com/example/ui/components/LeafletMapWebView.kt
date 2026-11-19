package com.example.ui.components

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.LiveLocation
import com.example.util.HistoryVisitNode
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale

private const val TAG = "LeafletMapWebView"
private const val MAX_RENDERER_RECOVERIES = 2

data class MapFocusRequest(
    val latitude: Double,
    val longitude: Double,
    val label: String,
    val triggerId: Long = System.currentTimeMillis()
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LeafletMapWebView(
    expenses: List<Expense>,
    currentUserName: String,
    userLocation: Pair<Double, Double>? = null,
    centerOnUserTrigger: Long = 0L,
    liveLocations: List<LiveLocation> = emptyList(),
    historyNodes: List<HistoryVisitNode> = emptyList(),
    showHistoryTrail: Boolean = false,
    fitHistoryTrigger: Long = 0L,
    focusRequest: MapFocusRequest? = null,
    onMarkerClick: ((Expense) -> Unit)? = null,
    onLiveMarkerClick: ((LiveLocation) -> Unit)? = null,
    onHistoryNodeClick: ((HistoryVisitNode) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isMapLoaded by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var hasCenteredInitialUser by remember { mutableStateOf(false) }
    var rendererCrashCount by remember { mutableIntStateOf(0) }
    var webViewGeneration by remember { mutableIntStateOf(0) }
    var useSoftwareSafeMode by remember { mutableStateOf(false) }
    var isWebViewUnavailable by remember { mutableStateOf(false) }

    val validExpenses = remember(expenses) {
        expenses.filter { it.latitude != null && it.longitude != null }
    }

    val jsonExpenses = remember(validExpenses, currentUserName) {
        buildJsonArray {
            validExpenses.forEach { exp ->
                val category = ExpenseCategory.fromString(exp.category)
                val isCurrent = exp.user_name.equals(currentUserName, ignoreCase = true)
                val markerColor = if (isCurrent) "#E54D68" else "#4A69BD"

                add(buildJsonObject {
                    put("id", exp.id)
                    put("lat", exp.latitude ?: 0.0)
                    put("lng", exp.longitude ?: 0.0)
                    put("userName", exp.user_name)
                    put("amount", exp.amount)
                    put("category", category.title)
                    put("categoryColor", category.colorHex)
                    put("note", exp.note.ifBlank { category.title })
                    put("date", exp.date)
                    put("color", markerColor)
                    put("isCurrent", isCurrent)
                })
            }
        }.toString()
    }

    val jsonLiveLocations = remember(liveLocations, currentUserName) {
        buildJsonArray {
            liveLocations.forEach { loc ->
                val isCurrent = loc.user_name.equals(currentUserName, ignoreCase = true)
                val markerColor = if (isCurrent) "#E54D68" else "#4A69BD"

                add(buildJsonObject {
                    put("id", loc.id)
                    put("userName", loc.user_name)
                    put("lat", loc.latitude)
                    put("lng", loc.longitude)
                    put("color", markerColor)
                    put("isCurrent", isCurrent)
                    put("isSharing", loc.is_sharing)
                    put("placeName", loc.place_name)
                    put("timestamp", loc.timestamp)
                    put("battery", loc.battery_level ?: -1)
                    put("updatedAt", loc.updated_at)
                })
            }
        }.toString()
    }

    // Chronological ascending for clean per-user polyline + numbered dots
    val chronologicalNodes = remember(historyNodes) {
        historyNodes.sortedBy { it.arrivalMillis }
    }

    val jsonHistoryNodes = remember(chronologicalNodes) {
        buildJsonArray {
            chronologicalNodes.forEach { node ->
                val markerColor = if (node.isCurrentUser) "#E54D68" else "#4A69BD"
                add(buildJsonObject {
                    put("id", node.id)
                    put("userName", node.userName)
                    put("lat", node.latitude)
                    put("lng", node.longitude)
                    put("placeName", node.placeName)
                    put("date", node.date)
                    put("arrivalTime", node.arrivalTimeExact)
                    put("departureTime", node.departureTimeExact)
                    put("stayDuration", node.stayDurationLabel)
                    put("isStayPoint", node.isStayPoint)
                    put("pointCount", node.pointCount)
                    put("stepIndex", node.stepIndex)
                    put("color", markerColor)
                    put("isCurrent", node.isCurrentUser)
                })
            }
        }.toString()
    }

    // Always keep latest lists & callbacks visible inside JavascriptInterface without stale closures
    val latestValidExpenses by rememberUpdatedState(validExpenses)
    val latestLiveLocations by rememberUpdatedState(liveLocations)
    val latestChronologicalNodes by rememberUpdatedState(chronologicalNodes)
    val latestJsonExpenses by rememberUpdatedState(jsonExpenses)
    val latestJsonLive by rememberUpdatedState(jsonLiveLocations)
    val latestJsonHistory by rememberUpdatedState(jsonHistoryNodes)
    val latestShowHistoryTrail by rememberUpdatedState(showHistoryTrail)
    val latestOnMarkerClick by rememberUpdatedState(onMarkerClick)
    val latestOnLiveMarkerClick by rememberUpdatedState(onLiveMarkerClick)
    val latestOnHistoryNodeClick by rememberUpdatedState(onHistoryNodeClick)

    val userLat = userLocation?.first
    val userLng = userLocation?.second

    // Default map center prioritizes current user's real location, partner's ping, or logged expenses
    val initialCenterLat = remember(webViewGeneration) {
        userLat
            ?: liveLocations.firstOrNull { it.user_name.equals(currentUserName, ignoreCase = true) }?.latitude
            ?: liveLocations.firstOrNull { !it.user_name.equals(currentUserName, ignoreCase = true) }?.latitude
            ?: validExpenses.firstOrNull()?.latitude
    }
    val initialCenterLng = remember(webViewGeneration) {
        userLng
            ?: liveLocations.firstOrNull { it.user_name.equals(currentUserName, ignoreCase = true) }?.longitude
            ?: liveLocations.firstOrNull { !it.user_name.equals(currentUserName, ignoreCase = true) }?.longitude
            ?: validExpenses.firstOrNull()?.longitude
    }
    val hasInitialRealCoords = initialCenterLat != null && initialCenterLng != null

    val htmlContent = remember(webViewGeneration, useSoftwareSafeMode) {
        generateLeafletHtml(
            initialLat = initialCenterLat ?: 0.0,
            initialLng = initialCenterLng ?: 0.0,
            initialZoom = if (hasInitialRealCoords) 15 else 2,
            lowMemorySafeMode = useSoftwareSafeMode
        )
    }

    // Push expense markers
    LaunchedEffect(jsonExpenses, isMapLoaded, webViewGeneration) {
        if (isMapLoaded) {
            safeEvaluateJs(webViewInstance, "if (window.updateExpenses) { window.updateExpenses($jsonExpenses); }")
        }
    }

    // Push live couple markers
    LaunchedEffect(jsonLiveLocations, isMapLoaded, webViewGeneration) {
        if (isMapLoaded) {
            safeEvaluateJs(webViewInstance, "if (window.updateLiveLocations) { window.updateLiveLocations($jsonLiveLocations); }")
        }
    }

    // Push going history dots + stay points trail
    LaunchedEffect(jsonHistoryNodes, showHistoryTrail, fitHistoryTrigger, isMapLoaded, webViewGeneration) {
        if (isMapLoaded) {
            val shouldFit = showHistoryTrail && fitHistoryTrigger > 0L
            safeEvaluateJs(
                webViewInstance,
                "if (window.updateHistoryTrail) { window.updateHistoryTrail($jsonHistoryNodes, $showHistoryTrail, $shouldFit); }"
            )
        }
    }

    // Keep user's current location dot synced live; center automatically once on initial fix
    LaunchedEffect(userLocation, isMapLoaded, webViewGeneration) {
        if (isMapLoaded && userLat != null && userLng != null) {
            val autoCenter = !hasCenteredInitialUser && !showHistoryTrail
            if (autoCenter) {
                hasCenteredInitialUser = true
            }
            safeEvaluateJs(
                webViewInstance,
                "if (window.updateCurrentUserMarker) { window.updateCurrentUserMarker($userLat, $userLng, $autoCenter, false); }"
            )
        }
    }

    // Explicit center & popup when user taps GPS button
    LaunchedEffect(centerOnUserTrigger, isMapLoaded, webViewGeneration) {
        if (centerOnUserTrigger > 0L && isMapLoaded && userLat != null && userLng != null) {
            safeEvaluateJs(
                webViewInstance,
                "if (window.updateCurrentUserMarker) { window.updateCurrentUserMarker($userLat, $userLng, true, true); }"
            )
        }
    }

    // Fly map to specific focus target (partner profile, user profile, or history waypoint)
    LaunchedEffect(focusRequest?.triggerId, isMapLoaded, webViewGeneration) {
        val req = focusRequest
        if (req != null && isMapLoaded) {
            val escapedLabel = escapeJsString(req.label)
            safeEvaluateJs(
                webViewInstance,
                "if (window.focusLocation) { window.focusLocation(${req.latitude}, ${req.longitude}, '$escapedLabel'); }"
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (!isWebViewUnavailable && rendererCrashCount <= MAX_RENDERER_RECOVERIES) {
            key(webViewGeneration) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        try {
                            WebView(ctx).apply {
                                webViewInstance = this
                                // Avoid forced LAYER_TYPE_HARDWARE FBO double-buffering (major cause of Chromium renderer code -1 crashes).
                                // After any renderer crash, fall back explicitly to LAYER_TYPE_SOFTWARE for maximum stability.
                                setLayerType(
                                    if (useSoftwareSafeMode) View.LAYER_TYPE_SOFTWARE else View.LAYER_TYPE_NONE,
                                    null
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                                }
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    setGeolocationEnabled(false)
                                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    userAgentString = "CoupleExpenseApp/1.0 (Android; OpenStreetMap Free Leaflet)"
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                        Log.d(TAG, "JS Console: ${consoleMessage?.message()}")
                                        return true
                                    }
                                }

                                webViewClient = createSafeMapWebViewClient(
                                    onMainFrameError = { msg ->
                                        hasError = true
                                        errorMessage = msg
                                    },
                                    onPageFinishedCallback = { view ->
                                        val js = "if (window.initMapData) { window.initMapData($latestJsonExpenses, $latestJsonLive, $latestJsonHistory, $latestShowHistoryTrail); }"
                                        safeEvaluateJs(view, js)
                                    },
                                    onRendererCrash = { view ->
                                        destroyWebViewSafely(view)
                                        if (webViewInstance === view) {
                                            webViewInstance = null
                                        }
                                        isMapLoaded = false
                                        val nextCount = rendererCrashCount + 1
                                        rendererCrashCount = nextCount
                                        if (nextCount <= MAX_RENDERER_RECOVERIES) {
                                            useSoftwareSafeMode = true
                                            hasError = false
                                            errorMessage = null
                                            webViewGeneration += 1
                                        } else {
                                            hasError = true
                                            errorMessage = "Map renderer stopped repeatedly. Tap Retry Map to reload."
                                        }
                                    }
                                )

                                addJavascriptInterface(object {
                                    @JavascriptInterface
                                    fun onMarkerSelected(expenseId: String) {
                                        val exp = latestValidExpenses.firstOrNull { it.id == expenseId }
                                        if (exp != null) {
                                            post { latestOnMarkerClick?.invoke(exp) }
                                        }
                                    }

                                    @JavascriptInterface
                                    fun onLiveMarkerSelected(liveId: String) {
                                        val loc = latestLiveLocations.firstOrNull { it.id == liveId }
                                        if (loc != null) {
                                            post { latestOnLiveMarkerClick?.invoke(loc) }
                                        }
                                    }

                                    @JavascriptInterface
                                    fun onHistoryNodeSelected(nodeId: String) {
                                        val node = latestChronologicalNodes.firstOrNull { it.id == nodeId }
                                        if (node != null) {
                                            post { latestOnHistoryNodeClick?.invoke(node) }
                                        }
                                    }

                                    @JavascriptInterface
                                    fun onMapReady() {
                                        post {
                                            isMapLoaded = true
                                            hasError = false
                                        }
                                    }

                                    @JavascriptInterface
                                    fun onMapError(err: String) {
                                        Log.e(TAG, "Leaflet Map Error: $err")
                                        post {
                                            hasError = true
                                            errorMessage = err
                                        }
                                    }
                                }, "AndroidBridge")

                                loadDataWithBaseURL(
                                    "https://www.openstreetmap.org",
                                    htmlContent,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "Unable to initialize Android WebView: ${t.message}", t)
                            isWebViewUnavailable = true
                            hasError = true
                            errorMessage = "Android System WebView is unavailable or updating."
                            View(ctx)
                        }
                    },
                    update = { _ -> },
                    onRelease = { releasedView ->
                        if (releasedView is WebView) {
                            if (webViewInstance === releasedView) {
                                webViewInstance = null
                            }
                            destroyWebViewSafely(releasedView)
                        }
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = !isMapLoaded && !hasError && !isWebViewUnavailable && rendererCrashCount <= MAX_RENDERER_RECOVERIES,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (useSoftwareSafeMode) "Recovering Couple Map..." else "Loading Couple Map...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = hasError || isWebViewUnavailable || rendererCrashCount > MAX_RENDERER_RECOVERIES,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(20.dp)
                .testTag("map_error_recovery_card")
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(42.dp)
                    )
                    Text(
                        text = "Map Connection Issue",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = errorMessage ?: "Please check internet connection",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (userLat != null && userLng != null) {
                        Text(
                            text = String.format(Locale.US, "Your GPS: %.5f, %.5f", userLat, userLng),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            isWebViewUnavailable = false
                            rendererCrashCount = 0
                            useSoftwareSafeMode = true
                            hasError = false
                            errorMessage = null
                            isMapLoaded = false
                            webViewGeneration += 1
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("retry_map_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Retry Map")
                    }
                }
            }
        }
    }
}

internal fun createSafeMapWebViewClient(
    onMainFrameError: (String?) -> Unit,
    onPageFinishedCallback: (WebView?) -> Unit,
    onRendererCrash: (WebView?) -> Unit
): WebViewClient {
    return object : WebViewClient() {
        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            if (request?.isForMainFrame == true) {
                onMainFrameError(error?.description?.toString())
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            onPageFinishedCallback(view)
        }

        override fun onRenderProcessGone(
            view: WebView?,
            detail: RenderProcessGoneDetail?
        ): Boolean {
            val crashed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                detail?.didCrash() ?: true
            } else {
                true
            }
            Log.w(TAG, "WebView renderer process gone (didCrash=$crashed); recovering safely without app crash")
            onRendererCrash(view)
            // Always return true so Chromium's aw_browser_terminator does NOT terminate the host app.
            return true
        }
    }
}

private fun safeEvaluateJs(webView: WebView?, script: String) {
    if (webView == null) return
    runCatching {
        if (webView.isAttachedToWindow) {
            webView.evaluateJavascript(script, null)
        }
    }.onFailure { err ->
        Log.w(TAG, "Skipped JS evaluation on detached/crashed WebView: ${err.message}")
    }
}

private fun destroyWebViewSafely(webView: WebView?) {
    if (webView == null) return
    runCatching {
        webView.stopLoading()
        webView.removeJavascriptInterface("AndroidBridge")
        webView.loadUrl("about:blank")
        webView.clearHistory()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.removeAllViews()
        webView.destroy()
    }.onFailure { err ->
        Log.w(TAG, "WebView cleanup warning: ${err.message}")
    }
}

private fun escapeJsString(input: String): String {
    return input
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", " ")
        .replace("\r", " ")
}

private fun generateLeafletHtml(
    initialLat: Double,
    initialLng: Double,
    initialZoom: Int = 15,
    lowMemorySafeMode: Boolean = false
): String {
    val pulseAnimationCss = if (lowMemorySafeMode) {
        "animation: none; opacity: 0.3;"
    } else {
        "animation: live-pulse 2s infinite ease-out;"
    }
    val zoomAnimationJs = !lowMemorySafeMode

    return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <title>Couple Live Map & History</title>
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" onerror="this.onerror=null;this.href='https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css';" />
    <style>
        * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
        html, body {
            width: 100%; height: 100%; min-height: 100%;
            margin: 0; padding: 0; overflow: hidden;
            position: fixed; top: 0; left: 0; right: 0; bottom: 0;
            background: #FAF7F5;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
        }
        #map {
            position: absolute; top: 0; left: 0; right: 0; bottom: 0;
            width: 100%; height: 100%; min-height: 100%;
            background: #FAF7F5; z-index: 1;
        }
        .custom-pin {
            display: flex; align-items: center; justify-content: center;
            border-radius: 50%; color: white; font-weight: 800; font-size: 11px;
            box-shadow: 0 4px 10px rgba(0,0,0,0.3);
            border: 2.5px solid #ffffff;
        }
        @keyframes live-pulse {
            0% { transform: scale(0.85); opacity: 0.9; }
            50% { transform: scale(1.65); opacity: 0.25; }
            100% { transform: scale(2.25); opacity: 0; }
        }
        .live-marker-box {
            position: relative; width: 44px; height: 44px;
            display: flex; align-items: center; justify-content: center;
        }
        .live-pulse-wave {
            position: absolute; width: 40px; height: 40px;
            border-radius: 50%; $pulseAnimationCss
            pointer-events: none;
        }
        .live-avatar-disc {
            position: relative; width: 32px; height: 32px;
            border-radius: 50%; border: 3px solid #ffffff;
            box-shadow: 0 4px 12px rgba(0,0,0,0.35);
            display: flex; align-items: center; justify-content: center;
            color: white; font-weight: 800; font-size: 13px;
            z-index: 2; cursor: pointer;
        }
        .gps-self-box {
            position: relative; width: 32px; height: 32px;
            display: flex; align-items: center; justify-content: center;
        }
        .gps-self-halo {
            position: absolute; width: 28px; height: 28px; border-radius: 50%;
            background: rgba(46, 125, 50, 0.35);
            $pulseAnimationCss
        }
        .gps-self-dot {
            position: relative; width: 15px; height: 15px; border-radius: 50%;
            background: #2E7D32; border: 3px solid #FFFFFF;
            box-shadow: 0 2px 8px rgba(0,0,0,0.35); z-index: 2;
        }

        /* History Going Dot (Movement waypoint) */
        .history-going-dot {
            width: 20px; height: 20px; border-radius: 50%;
            border: 2.5px solid #ffffff;
            box-shadow: 0 2px 6px rgba(0,0,0,0.28);
            display: flex; align-items: center; justify-content: center;
            color: #ffffff; font-size: 9px; font-weight: 800;
            cursor: pointer;
        }

        /* History Stay Point Marker (Dwell halo + pin + duration badge) */
        .history-stay-wrap {
            position: relative; display: flex; flex-direction: column; align-items: center;
        }
        .history-stay-pin {
            width: 28px; height: 28px; border-radius: 50%;
            border: 3px solid #ffffff;
            box-shadow: 0 0 0 5px rgba(255, 179, 0, 0.35), 0 4px 10px rgba(0,0,0,0.35);
            display: flex; align-items: center; justify-content: center;
            color: #ffffff; font-size: 11px; font-weight: 900;
            cursor: pointer;
        }
        .history-stay-badge {
            margin-top: 2px; padding: 1px 5px; border-radius: 6px;
            background: #201A1B; color: #FFFFFF; font-size: 9px; font-weight: 700;
            white-space: nowrap; box-shadow: 0 2px 4px rgba(0,0,0,0.25);
        }

        .leaflet-popup-content-wrapper {
            border-radius: 16px; padding: 6px;
            box-shadow: 0 6px 20px rgba(0,0,0,0.18);
        }
        .popup-card { font-size: 13px; line-height: 1.4; min-width: 175px; cursor: pointer; }
        .popup-amount { font-size: 16px; font-weight: 800; color: #E54D68; margin-bottom: 4px; }
        .popup-title { font-weight: 700; font-size: 14px; color: #201A1B; margin-bottom: 4px; }
        .popup-badge {
            display: inline-block; padding: 2px 8px; border-radius: 8px;
            font-size: 11px; font-weight: 700; color: white; margin-bottom: 5px;
        }
        .popup-meta { color: #5F5A62; font-size: 11px; }
        #loading-notice {
            position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%);
            z-index: 2; color: #8C7B7E; font-size: 13px; font-weight: 600; pointer-events: none;
        }
    </style>
</head>
<body>
    <div id="loading-notice">Rendering Map...</div>
    <div id="map"></div>

    <script>
        function loadScript(src, fallbackSrc, callback) {
            const script = document.createElement('script');
            script.src = src;
            script.onload = () => {
                if (typeof L !== 'undefined') callback();
                else if (fallbackSrc) loadScript(fallbackSrc, null, callback);
            };
            script.onerror = () => {
                if (fallbackSrc) loadScript(fallbackSrc, null, callback);
                else if (window.AndroidBridge) window.AndroidBridge.onMapError("Failed to load map scripts");
            };
            document.head.appendChild(script);
        }

        loadScript(
            "https://unpkg.com/leaflet@1.9.4/dist/leaflet.js",
            "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js",
            initLeafletMap
        );

        let map = null;
        let markersGroup = null;
        let liveGroup = null;
        let historyGroup = null;
        let userLocationMarker = null;

        function initLeafletMap() {
            try {
                const notice = document.getElementById('loading-notice');
                if (notice) notice.style.display = 'none';

                map = L.map('map', {
                    preferCanvas: true,
                    zoomControl: true,
                    attributionControl: false,
                    fadeAnimation: $zoomAnimationJs,
                    zoomAnimation: $zoomAnimationJs,
                    markerZoomAnimation: $zoomAnimationJs
                }).setView([$initialLat, $initialLng], $initialZoom);

                const osmLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                    maxZoom: 19,
                    attribution: '&copy; OpenStreetMap contributors'
                }).addTo(map);

                osmLayer.on('tileerror', function() {
                    if (!window.osmFallbackAdded) {
                        window.osmFallbackAdded = true;
                        L.tileLayer('https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png', {
                            subdomains: ['a', 'b'],
                            maxZoom: 19
                        }).addTo(map);
                    }
                });

                markersGroup = L.featureGroup().addTo(map);
                liveGroup = L.featureGroup().addTo(map);
                historyGroup = L.featureGroup().addTo(map);

                function forceResize() { if (map) map.invalidateSize(true); }
                setTimeout(forceResize, 50);
                setTimeout(forceResize, 250);
                setTimeout(forceResize, 700);
                window.addEventListener('resize', forceResize);

                if (window.AndroidBridge) window.AndroidBridge.onMapReady();

                if (window.pendingData) {
                    const data = window.pendingData;
                    if (data.expenses) window.updateExpenses(data.expenses);
                    if (data.live) window.updateLiveLocations(data.live);
                    if (data.history) window.updateHistoryTrail(data.history, data.showTrail, false);
                    window.pendingData = null;
                }
            } catch (err) {
                if (window.AndroidBridge) window.AndroidBridge.onMapError(err.message || "Map init failed");
            }
        }

        window.initMapData = function(expensesList, liveList, historyList, showTrail) {
            if (!map) {
                window.pendingData = { expenses: expensesList, live: liveList, history: historyList, showTrail: showTrail };
            } else {
                window.updateExpenses(expensesList);
                window.updateLiveLocations(liveList);
                window.updateHistoryTrail(historyList, showTrail, false);
            }
        };

        window.updateExpenses = function(expensesList) {
            if (!map || !markersGroup) {
                if (!window.pendingData) window.pendingData = {};
                window.pendingData.expenses = expensesList;
                return;
            }
            markersGroup.clearLayers();
            if (expensesList && expensesList.length > 0) {
                expensesList.forEach(item => {
                    const pinIcon = L.divIcon({
                        className: 'custom-div-icon',
                        html: '<div class="custom-pin" style="background-color: ' + item.color + '; width: 26px; height: 26px;">Ks</div>',
                        iconSize: [26, 26], iconAnchor: [13, 13], popupAnchor: [0, -13]
                    });
                    const marker = L.marker([item.lat, item.lng], { icon: pinIcon });
                    const popupHtml =
                        '<div class="popup-card" onclick="if(window.AndroidBridge){ AndroidBridge.onMarkerSelected(\'' + item.id + '\'); }">' +
                            '<div class="popup-amount">' + Math.round(item.amount).toLocaleString() + ' MMK</div>' +
                            '<div class="popup-title">' + (item.note || item.category) + '</div>' +
                            '<div class="popup-badge" style="background-color: ' + item.color + '">' + item.userName + '</div>' +
                            '<div class="popup-meta">🏷️ ' + item.category + ' • 📅 ' + item.date + '</div>' +
                        '</div>';
                    marker.bindPopup(popupHtml);
                    markersGroup.addLayer(marker);
                });
            }
        };

        window.updateLiveLocations = function(liveList) {
            if (!map || !liveGroup) {
                if (!window.pendingData) window.pendingData = {};
                window.pendingData.live = liveList;
                return;
            }
            liveGroup.clearLayers();
            if (liveList && liveList.length > 0) {
                liveList.forEach(loc => {
                    const initial = (loc.userName || "?").charAt(0).toUpperCase();
                    const waveColor = loc.color || '#E54D68';
                    const batteryText = loc.battery >= 0 ? (' • 🔋 ' + loc.battery + '%') : '';
                    const isSharing = loc.isSharing !== false;
                    const waveHtml = isSharing ? '<div class="live-pulse-wave" style="background-color: ' + waveColor + ';"></div>' : '';

                    const liveHtml =
                        '<div class="live-marker-box">' +
                            waveHtml +
                            '<div class="live-avatar-disc" style="background-color: ' + waveColor + '; opacity: ' + (isSharing ? '1' : '0.82') + ';">' + initial + '</div>' +
                        '</div>';

                    const liveIcon = L.divIcon({
                        className: 'live-div-icon',
                        html: liveHtml, iconSize: [44, 44], iconAnchor: [22, 22], popupAnchor: [0, -22]
                    });

                    const marker = L.marker([loc.lat, loc.lng], { icon: liveIcon, zIndexOffset: 1000 });
                    const statusLine = isSharing
                        ? '<div style="font-size:12px; font-weight:700; color:#2E7D32; margin-bottom:4px;">🟢 Live Now' + batteryText + '</div>'
                        : '<div style="font-size:12px; font-weight:700; color:#757575; margin-bottom:4px;">📍 Last Known' + batteryText + '</div>';

                    const popupContent =
                        '<div class="popup-card" onclick="if(window.AndroidBridge){ AndroidBridge.onLiveMarkerSelected(\'' + loc.id + '\'); }">' +
                            '<div style="display:flex; align-items:center; margin-bottom:6px;">' +
                                '<span style="font-size:16px; margin-right:6px;">' + (loc.isCurrent ? '👤' : '❤️') + '</span>' +
                                '<span style="font-weight:800; font-size:14px; color:#201A1B;">' + loc.userName + '</span>' +
                            '</div>' +
                            statusLine +
                            (loc.placeName ? '<div style="font-size:12px; color:#49454E; margin-bottom:4px;">📍 ' + loc.placeName + '</div>' : '') +
                            '<div class="popup-meta">Exact Time: ' + (loc.timestamp || 'Just now') + '</div>' +
                        '</div>';

                    marker.bindPopup(popupContent);
                    marker.on('click', function() {
                        if (window.AndroidBridge) AndroidBridge.onLiveMarkerSelected(loc.id);
                    });
                    liveGroup.addLayer(marker);
                });
            }
        };

        window.updateHistoryTrail = function(historyList, showTrail, fitBounds) {
            if (!map || !historyGroup) {
                if (!window.pendingData) window.pendingData = {};
                window.pendingData.history = historyList;
                window.pendingData.showTrail = showTrail;
                return;
            }
            historyGroup.clearLayers();

            if (showTrail && historyList && historyList.length > 0) {
                const byUser = {};
                historyList.forEach(pt => {
                    const key = (pt.userName || 'user').toLowerCase();
                    if (!byUser[key]) byUser[key] = [];
                    byUser[key].push(pt);
                });

                Object.keys(byUser).forEach(key => {
                    const pts = byUser[key];
                    const routeCoords = [];
                    const routeColor = (pts[0] && pts[0].color) ? pts[0].color : '#E54D68';

                    pts.forEach(point => {
                        routeCoords.push([point.lat, point.lng]);
                        const isStay = !!point.isStayPoint;
                        let icon;
                        if (isStay) {
                            const stayBadge = point.stayDuration ? '<div class="history-stay-badge">' + point.stayDuration + '</div>' : '';
                            icon = L.divIcon({
                                className: 'history-stay-icon',
                                html: '<div class="history-stay-wrap">' +
                                        '<div class="history-stay-pin" style="background-color:' + routeColor + ';">' + point.stepIndex + '</div>' +
                                        stayBadge +
                                      '</div>',
                                iconSize: [70, 46], iconAnchor: [35, 14], popupAnchor: [0, -14]
                            });
                        } else {
                            icon = L.divIcon({
                                className: 'history-going-icon',
                                html: '<div class="history-going-dot" style="background-color:' + routeColor + ';">' + point.stepIndex + '</div>',
                                iconSize: [20, 20], iconAnchor: [10, 10], popupAnchor: [0, -10]
                            });
                        }

                        const waypoint = L.marker([point.lat, point.lng], {
                            icon: icon,
                            zIndexOffset: isStay ? 800 : 500
                        });

                        const timeDetail = isStay && point.departureTime && point.departureTime !== point.arrivalTime
                            ? ('🕒 ' + point.arrivalTime + ' → ' + point.departureTime)
                            : ('🕒 ' + point.arrivalTime);
                        const stayLine = isStay
                            ? '<div style="font-size:11px; font-weight:700; color:#E65100; margin-bottom:4px;">📌 ' + (point.stayDuration || 'Stayed Here') + ' (' + point.pointCount + ' logs)</div>'
                            : '<div style="font-size:11px; font-weight:600; color:' + routeColor + '; margin-bottom:4px;">● Going Point #' + point.stepIndex + '</div>';

                        waypoint.bindPopup(
                            '<div class="popup-card" onclick="if(window.AndroidBridge){ AndroidBridge.onHistoryNodeSelected(\'' + point.id + '\'); }">' +
                                '<div class="popup-title">📍 ' + (point.placeName || 'Location Point') + '</div>' +
                                stayLine +
                                '<div class="popup-meta">' + point.userName + ' • ' + point.date + '</div>' +
                                '<div class="popup-meta" style="font-weight:700; color:#201A1B; margin-top:2px;">' + timeDetail + '</div>' +
                            '</div>'
                        );
                        waypoint.on('click', function() {
                            if (window.AndroidBridge) AndroidBridge.onHistoryNodeSelected(point.id);
                        });
                        historyGroup.addLayer(waypoint);
                    });

                    if (routeCoords.length > 1) {
                        const polyline = L.polyline(routeCoords, {
                            color: routeColor,
                            weight: 4,
                            opacity: 0.85,
                            dashArray: '6, 8'
                        });
                        historyGroup.addLayer(polyline);
                    }
                });

                if (fitBounds && historyGroup.getLayers().length > 0) {
                    try {
                        map.fitBounds(historyGroup.getBounds().pad(0.22), { maxZoom: 16 });
                    } catch (e) {}
                }
            }
        };

        window.updateCurrentUserMarker = function(lat, lng, centerMap, openPopup) {
            if (!map) return;
            if (!userLocationMarker) {
                const gpsIcon = L.divIcon({
                    className: 'gps-self-icon',
                    html: '<div class="gps-self-box"><div class="gps-self-halo"></div><div class="gps-self-dot"></div></div>',
                    iconSize: [32, 32], iconAnchor: [16, 16], popupAnchor: [0, -14]
                });
                userLocationMarker = L.marker([lat, lng], { icon: gpsIcon, zIndexOffset: 900 })
                    .addTo(map)
                    .bindPopup('<div class="popup-card"><div class="popup-title">📍 Your Current Location</div><div class="popup-meta">' + lat.toFixed(5) + ', ' + lng.toFixed(5) + '</div></div>');
            } else {
                userLocationMarker.setLatLng([lat, lng]);
                userLocationMarker.setPopupContent('<div class="popup-card"><div class="popup-title">📍 Your Current Location</div><div class="popup-meta">' + lat.toFixed(5) + ', ' + lng.toFixed(5) + '</div></div>');
            }
            if (centerMap) {
                map.setView([lat, lng], 16, { animate: $zoomAnimationJs });
            }
            if (openPopup) {
                userLocationMarker.openPopup();
            }
            map.invalidateSize(true);
        };

        window.focusLocation = function(lat, lng, name) {
            if (map) {
                map.setView([lat, lng], 16, { animate: $zoomAnimationJs });
            }
        };
    </script>
</body>
</html>
    """.trimIndent()
}
