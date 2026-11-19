package com.example.data.local

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.UUID

class DeviceHelper(private val context: Context) {

    private val prefs by lazy {
        context.getSharedPreferences("couple_device_identity", Context.MODE_PRIVATE)
    }

    @SuppressLint("HardwareIds")
    fun getDeviceId(): String {
        val cached = prefs.getString("persisted_device_id", null)
        if (!cached.isNullOrBlank()) {
            return cached
        }

        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (_: Exception) {
            null
        }

        val resolvedId = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            "android-$androidId"
        } else {
            "device-${UUID.randomUUID()}"
        }

        prefs.edit().putString("persisted_device_id", resolvedId).apply()
        return resolvedId
    }

    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        return when {
            manufacturer.isNotBlank() && model.isNotBlank() -> {
                if (model.startsWith(manufacturer, ignoreCase = true)) {
                    model
                } else {
                    "${manufacturer.replaceFirstChar { it.uppercase() }} $model"
                }
            }
            model.isNotBlank() -> model
            else -> "Android Device"
        }
    }
}
