package com.example.data.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.CoupleAppContainer
import com.example.data.model.LiveLocation
import com.example.data.model.LocationHistoryItem
import com.example.util.LocationHistoryAnalyzer
import com.example.util.PermissionHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Play-compliant Foreground Service (foregroundServiceType="location") for continuous
 * live location broadcasting & exact history / stay-point recording while backgrounded.
 */
class LocationTrackingService : Service() {

    private val serviceErrorHandler = CoroutineExceptionHandler { _, throwable ->
        Log.w(TAG, "Background location service error: ${throwable.message}")
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + serviceErrorHandler)
    private var locationLoopJob: Job? = null
    private lateinit var locationHelper: LocationHelper

    override fun onCreate() {
        super.onCreate()
        locationHelper = LocationHelper(this)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            serviceScope.launch {
                try {
                    val prefs = CoupleAppContainer.getPreferences(applicationContext)
                    val session = prefs.userSessionFlow.first()
                    prefs.setLocationSharing(false, userExplicit = true)
                    if (session.coupleId.isNotBlank() && session.userName.isNotBlank()) {
                        val repo = CoupleAppContainer.getRepository(applicationContext)
                        val existing = repo.liveLocationsFlow.first().firstOrNull {
                            it.user_name.equals(session.userName, ignoreCase = true)
                        }
                        if (existing != null) {
                            repo.updateLiveLocation(existing.copy(is_sharing = false))
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping location sharing: ${e.message}")
                } finally {
                    runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                    runCatching { stopSelf() }
                }
            }
            return START_NOT_STICKY
        }

        if (!PermissionHelper.hasLocationPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification("Sharing live location with partner...")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enter foreground location service: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        startContinuousTracking()
        return START_STICKY
    }

    private fun startContinuousTracking() {
        if (locationLoopJob?.isActive == true) return
        locationLoopJob = serviceScope.launch {
            try {
                val prefs = CoupleAppContainer.getPreferences(applicationContext)
                val repo = CoupleAppContainer.getRepository(applicationContext)

                // Push an immediate initial fix
                locationHelper.getCurrentCoordinates(highAccuracy = true)?.let { coords ->
                    publishLocationPoint(coords.first, coords.second, prefs, repo)
                }

                // Stream continuous GPS updates while backgrounded or foregrounded
                locationHelper.locationUpdatesFlow(
                    intervalMs = 15_000L,
                    minDistanceMeters = 10f,
                    highAccuracy = true
                ).collect { (lat, lng) ->
                    val session = prefs.userSessionFlow.first()
                    if (!session.isLocationSharingEnabled || !session.isPaired) {
                        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                        runCatching { stopSelf() }
                        return@collect
                    }
                    publishLocationPoint(lat, lng, prefs, repo)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Location tracking loop warning: ${e.message}")
            }
        }
    }

    private suspend fun publishLocationPoint(
        lat: Double,
        lng: Double,
        prefs: com.example.data.local.CouplePreferences,
        repo: com.example.data.supabase.SupabaseRepository
    ) {
        val session = prefs.userSessionFlow.first()
        if (!session.isPaired || session.coupleId.isBlank() || session.userName.isBlank()) return
        if (!session.isLocationSharingEnabled) return

        val nowMs = System.currentTimeMillis()
        val timeSdf = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
        val dateSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val now = Date(nowMs)
        val timeStr = timeSdf.format(now)
        val dateStr = dateSdf.format(now)
        val placeName = locationHelper.getPlaceName(lat, lng)
        val battery = locationHelper.getBatteryLevel()

        val liveLoc = LiveLocation(
            id = "loc-${session.coupleId}-${session.userName.lowercase(Locale.ROOT)}",
            couple_id = session.coupleId,
            user_name = session.userName,
            latitude = lat,
            longitude = lng,
            is_sharing = true,
            battery_level = battery,
            place_name = placeName,
            timestamp = timeStr,
            updated_at = nowMs
        )
        repo.updateLiveLocation(liveLoc)

        // Update notification text with latest place
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(NOTIFICATION_ID, buildNotification("Live at $placeName • $timeStr"))
        } catch (e: Exception) {
            Log.w(TAG, "Unable to update live location notification: ${e.message}")
        }

        // Record history breadcrumb if moved >= 35m OR stayed >= 2 min (captures stay duration cleanly)
        val lastHistory = repo.locationHistoryFlow.first()
            .firstOrNull { it.user_name.equals(session.userName, ignoreCase = true) }

        val movedMeters = if (lastHistory != null) {
            LocationHistoryAnalyzer.haversineMeters(
                lastHistory.latitude,
                lastHistory.longitude,
                lat,
                lng
            )
        } else {
            Double.MAX_VALUE
        }
        val elapsedMs = if (lastHistory != null) nowMs - lastHistory.recorded_at else Long.MAX_VALUE

        if (lastHistory == null || movedMeters >= 35.0 || elapsedMs >= 2 * 60 * 1000L) {
            val historyItem = LocationHistoryItem(
                id = UUID.randomUUID().toString(),
                couple_id = session.coupleId,
                user_name = session.userName,
                latitude = lat,
                longitude = lng,
                place_name = placeName,
                timestamp = timeStr,
                date = dateStr,
                recorded_at = nowMs
            )
            repo.recordLocationHistory(historyItem)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Partner Location Sharing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when Couple Expense is sharing your live location in the background."
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Couple Live Location Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_stat_couple_heart)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openPending)
            .addAction(0, "Pause Sharing", stopPending)
            .build()
    }

    override fun onDestroy() {
        locationLoopJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "LocationTrackingService"
        private const val CHANNEL_ID = "couple_live_location_channel"
        private const val NOTIFICATION_ID = 4102
        const val ACTION_STOP = "com.example.action.STOP_LIVE_LOCATION"

        fun start(context: Context) {
            if (!PermissionHelper.hasLocationPermission(context)) return
            val intent = Intent(context.applicationContext, LocationTrackingService::class.java)
            try {
                ContextCompat.startForegroundService(context.applicationContext, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to start LocationTrackingService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context.applicationContext, LocationTrackingService::class.java)
            try {
                context.applicationContext.stopService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to stop LocationTrackingService: ${e.message}")
            }
        }
    }
}
