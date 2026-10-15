package com.example.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

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

    @SuppressLint("MissingPermission")
    suspend fun getCurrentCoordinates(): Pair<Double, Double>? {
        if (!hasLocationPermission()) return null

        return try {
            val cts = CancellationTokenSource()
            val location: Location? = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cts.token
            ).await() ?: fusedLocationClient.lastLocation.await()

            if (location != null) {
                Pair(location.latitude, location.longitude)
            } else {
                // If emulator has no GPS fix yet, provide standard downtown default
                Pair(37.7749, -122.4194)
            }
        } catch (e: Exception) {
            null
        }
    }
}
