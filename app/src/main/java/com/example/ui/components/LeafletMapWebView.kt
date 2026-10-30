package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LeafletMapWebView(
    expenses: List<Expense>,
    currentUserName: String,
    onMarkerClick: ((Expense) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Prepare JSON data of coordinates and details for Leaflet
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

    val htmlContent = remember(jsonExpenses) {
        generateLeafletHtml(jsonExpenses)
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    cacheMode = WebSettings.LOAD_NO_CACHE
                }
                webViewClient = object : WebViewClient() {}
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onMarkerSelected(expenseId: String) {
                        val exp = validExpenses.firstOrNull { it.id == expenseId }
                        if (exp != null) {
                            onMarkerClick?.invoke(exp)
                        }
                    }
                }, "AndroidBridge")
                loadDataWithBaseURL("https://unpkg.com", htmlContent, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL("https://unpkg.com", htmlContent, "text/html", "UTF-8", null)
        }
    )
}

private fun generateLeafletHtml(jsonExpenses: String): String {
    return """
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
    <style>
        html, body, #map {
            width: 100%;
            height: 100%;
            margin: 0;
            padding: 0;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            background: #FAF7F5;
        }
        .custom-pin {
            display: flex;
            align-items: center;
            justify-content: center;
            border-radius: 50%;
            color: white;
            font-weight: bold;
            font-size: 11px;
            box-shadow: 0 3px 8px rgba(0,0,0,0.35);
            border: 2px solid #ffffff;
            transition: transform 0.2s ease;
        }
        .custom-pin:hover {
            transform: scale(1.15);
        }
        .leaflet-popup-content-wrapper {
            border-radius: 14px;
            padding: 4px;
            box-shadow: 0 4px 16px rgba(0,0,0,0.15);
        }
        .popup-card {
            font-size: 13px;
            line-height: 1.4;
            min-width: 150px;
        }
        .popup-title {
            font-weight: 700;
            font-size: 14px;
            color: #201A1B;
            margin-bottom: 2px;
        }
        .popup-amount {
            font-size: 16px;
            font-weight: 800;
            color: #E54D68;
            margin-bottom: 6px;
        }
        .popup-badge {
            display: inline-block;
            padding: 2px 8px;
            border-radius: 10px;
            font-size: 11px;
            font-weight: 600;
            color: white;
            margin-bottom: 4px;
        }
        .popup-meta {
            color: #787579;
            font-size: 11px;
        }
    </style>
</head>
<body>
    <div id="map"></div>
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
    <script>
        const expenses = $jsonExpenses;
        
        // Initial map view centered on default or first expense
        let initialLat = 37.7749;
        let initialLng = -122.4194;
        let initialZoom = 13;
        
        if (expenses.length > 0) {
            initialLat = expenses[0].lat;
            initialLng = expenses[0].lng;
        }

        const map = L.map('map', {
            zoomControl: true,
            attributionControl: false
        }).setView([initialLat, initialLng], initialZoom);

        // OpenStreetMap tile layer
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19
        }).addTo(map);

        const markers = [];

        expenses.forEach(item => {
            const pinIcon = L.divIcon({
                className: 'custom-div-icon',
                html: `<div class="custom-pin" style="background-color: ${'$'}{item.color}; width: 28px; height: 28px; font-size: 11px;">Ks</div>`,
                iconSize: [28, 28],
                iconAnchor: [14, 14],
                popupAnchor: [0, -14]
            });

            const marker = L.marker([item.lat, item.lng], { icon: pinIcon }).addTo(map);
            
            const popupHtml = `
                <div class="popup-card" onclick="if(window.AndroidBridge){ AndroidBridge.onMarkerSelected('${'$'}{item.id}'); }">
                    <div class="popup-amount">${'$'}{Math.round(item.amount).toLocaleString()} MMK</div>
                    <div class="popup-title">${'$'}{item.note}</div>
                    <div class="popup-badge" style="background-color: ${'$'}{item.color}">${'$'}{item.userName}</div>
                    <div class="popup-meta">🏷️ ${'$'}{item.category} • 📅 ${'$'}{item.date}</div>
                </div>
            `;
            marker.bindPopup(popupHtml);
            markers.push(marker);
        });

        // If multiple markers exist, fit map bounds nicely
        if (markers.length > 1) {
            const group = new L.featureGroup(markers);
            map.fitBounds(group.getBounds().pad(0.15));
        }
    </script>
</body>
</html>
    """.trimIndent()
}
