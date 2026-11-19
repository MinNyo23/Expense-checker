package com.example.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class LocationHelper(private val context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentCoordinates(highAccuracy: Boolean = false): Pair<Double, Double>? =
        withContext(Dispatchers.IO) {
            if (!hasLocationPermission()) return@withContext null

            val priority = if (highAccuracy && hasFineLocationPermission()) {
                Priority.PRIORITY_HIGH_ACCURACY
            } else {
                Priority.PRIORITY_BALANCED_POWER_ACCURACY
            }

            try {
                val cts = CancellationTokenSource()
                val liveFix: Location? = withTimeoutOrNull(4500L) {
                    fusedLocationClient.getCurrentLocation(priority, cts.token).await()
                }
                if (liveFix != null) {
                    return@withContext Pair(liveFix.latitude, liveFix.longitude)
                }

                val fusedLast = withTimeoutOrNull(1500L) {
                    fusedLocationClient.lastLocation.await()
                }
                if (fusedLast != null) {
                    return@withContext Pair(fusedLast.latitude, fusedLast.longitude)
                }

                val systemLast = getBestLastKnownSystemLocation()
                if (systemLast != null) {
                    return@withContext Pair(systemLast.latitude, systemLast.longitude)
                }

                null
            } catch (_: Exception) {
                getBestLastKnownSystemLocation()?.let { Pair(it.latitude, it.longitude) }
            }
        }

    @SuppressLint("MissingPermission")
    private fun getBestLastKnownSystemLocation(): Location? {
        if (!hasLocationPermission()) return null
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            providers.mapNotNull { provider ->
                try {
                    lm.getLastKnownLocation(provider)
                } catch (_: Exception) {
                    null
                }
            }.maxByOrNull { it.time }
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    fun locationUpdatesFlow(
        intervalMs: Long = 10_000L,
        minDistanceMeters: Float = 8f,
        highAccuracy: Boolean = true
    ): Flow<Pair<Double, Double>> = callbackFlow {
        if (!hasLocationPermission()) {
            close()
            return@callbackFlow
        }

        val priority = if (highAccuracy && hasFineLocationPermission()) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val request = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis((intervalMs / 2L).coerceAtLeast(3_000L))
            .setMinUpdateDistanceMeters(minDistanceMeters)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                trySend(Pair(loc.latitude, loc.longitude))
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (_: Exception) {
            close()
        }

        awaitClose {
            try {
                fusedLocationClient.removeLocationUpdates(callback)
            } catch (_: Exception) {
            }
        }
    }

    fun getBatteryLevel(): Int? {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            val level = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (level != null && level in 0..100) level else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getPlaceName(lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
            @Suppress("DEPRECATION")
            val list = geocoder.getFromLocation(lat, lng, 1)
            if (!list.isNullOrEmpty()) {
                val addr = list[0]
                val feature = addr.featureName
                val locality = addr.locality ?: addr.subAdminArea ?: ""
                val thoroughfare = addr.thoroughfare
                when {
                    !thoroughfare.isNullOrBlank() && locality.isNotBlank() -> "$thoroughfare, $locality"
                    !feature.isNullOrBlank() && locality.isNotBlank() -> "$feature, $locality"
                    locality.isNotBlank() -> locality
                    else -> String.format(java.util.Locale.US, "%.4f, %.4f", lat, lng)
                }
            } else {
                String.format(java.util.Locale.US, "%.4f, %.4f", lat, lng)
            }
        } catch (_: Exception) {
            String.format(java.util.Locale.US, "%.4f, %.4f", lat, lng)
        }
    }

    companion object {
        fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
            val results = FloatArray(1)
            Location.distanceBetween(lat1, lng1, lat2, lng2, results)
            return results[0]
        }
    }
}
