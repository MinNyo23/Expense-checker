package com.example.util

import com.example.data.model.LocationHistoryItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class HistoryVisitNode(
    val id: String,
    val userName: String,
    val latitude: Double,
    val longitude: Double,
    val placeName: String,
    val date: String,
    val arrivalMillis: Long,
    val departureMillis: Long,
    val arrivalTimeExact: String,
    val departureTimeExact: String,
    val stayDurationMillis: Long,
    val stayDurationLabel: String,
    val isStayPoint: Boolean,
    val pointCount: Int,
    val stepIndex: Int,
    val isCurrentUser: Boolean
)

/**
 * Groups raw chronological location breadcrumbs into exact-time Going Dots and Stay Points.
 *
 * - Consecutive pings by the same user within [STAY_RADIUS_METERS] on the same date are
 *   clustered together so dwell duration (arrival -> departure) is computed accurately.
 * - Any cluster with stay duration >= 2 minutes or >= 2 clustered pings is flagged as a
 *   Stay Point; intermediate moving points become Going Dots along the route trail.
 */
object LocationHistoryAnalyzer {

    private const val STAY_RADIUS_METERS = 85.0
    private const val STAY_MIN_DURATION_MS = 2 * 60 * 1000L
    private const val MAX_MERGE_GAP_MS = 45 * 60 * 1000L
    private val COORDINATE_PATTERN = Regex("""^-?\d+(\.\d+)?,\s*-?\d+(\.\d+)?$""")

    fun buildTimeline(
        items: List<LocationHistoryItem>,
        currentUserName: String
    ): List<HistoryVisitNode> {
        if (items.isEmpty()) return emptyList()

        val timeFormat = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val result = mutableListOf<HistoryVisitNode>()

        val byUser = items.groupBy { it.user_name.trim().lowercase(Locale.ROOT) }
        for ((_, userItems) in byUser) {
            val sorted = userItems.sortedBy { it.recorded_at }
            val userClusters = mutableListOf<MutableList<LocationHistoryItem>>()

            for (item in sorted) {
                val lastCluster = userClusters.lastOrNull()
                if (lastCluster == null) {
                    userClusters.add(mutableListOf(item))
                    continue
                }
                val anchor = lastCluster.first()
                val prev = lastCluster.last()
                val itemDate = item.date.ifBlank { dateFormat.format(Date(item.recorded_at)) }
                val anchorDate = anchor.date.ifBlank { dateFormat.format(Date(anchor.recorded_at)) }
                val dist = haversineMeters(anchor.latitude, anchor.longitude, item.latitude, item.longitude)
                val gap = (item.recorded_at - prev.recorded_at).coerceAtLeast(0L)

                if (itemDate == anchorDate && dist <= STAY_RADIUS_METERS && gap <= MAX_MERGE_GAP_MS) {
                    lastCluster.add(item)
                } else {
                    userClusters.add(mutableListOf(item))
                }
            }

            userClusters.forEachIndexed { index, cluster ->
                val first = cluster.first()
                val last = cluster.last()
                val arrivalMs = first.recorded_at
                val departureMs = last.recorded_at.coerceAtLeast(arrivalMs)
                val dwellMs = (departureMs - arrivalMs).coerceAtLeast(0L)

                val bestPlaceName = cluster
                    .map { it.place_name.trim() }
                    .firstOrNull { it.isNotBlank() && !COORDINATE_PATTERN.matches(it) }
                    ?: first.place_name.trim().ifBlank {
                        String.format(Locale.US, "%.4f, %.4f", first.latitude, first.longitude)
                    }

                val arrivalExact = if (arrivalMs > 0L) timeFormat.format(Date(arrivalMs)) else first.timestamp
                val departureExact = if (departureMs > 0L) timeFormat.format(Date(departureMs)) else last.timestamp
                val isStay = dwellMs >= STAY_MIN_DURATION_MS || cluster.size >= 2

                result.add(
                    HistoryVisitNode(
                        id = first.id.ifBlank { "${first.user_name}-$arrivalMs" },
                        userName = first.user_name,
                        latitude = cluster.map { it.latitude }.average(),
                        longitude = cluster.map { it.longitude }.average(),
                        placeName = bestPlaceName,
                        date = first.date.ifBlank { dateFormat.format(Date(arrivalMs)) },
                        arrivalMillis = arrivalMs,
                        departureMillis = departureMs,
                        arrivalTimeExact = arrivalExact.ifBlank { "Unknown" },
                        departureTimeExact = departureExact.ifBlank { arrivalExact },
                        stayDurationMillis = dwellMs,
                        stayDurationLabel = formatDuration(dwellMs, isStay),
                        isStayPoint = isStay,
                        pointCount = cluster.size,
                        stepIndex = index + 1,
                        isCurrentUser = first.user_name.equals(currentUserName, ignoreCase = true)
                    )
                )
            }
        }

        return result.sortedByDescending { it.departureMillis }
    }

    fun formatDuration(durationMillis: Long, isStay: Boolean): String {
        val totalMinutes = (durationMillis / 60_000L).toInt()
        val seconds = ((durationMillis / 1_000L) % 60L).toInt()
        return when {
            !isStay -> "Passing"
            totalMinutes >= 60 -> {
                val hrs = totalMinutes / 60
                val mins = totalMinutes % 60
                if (mins > 0) "Stayed ${hrs}h ${mins}m" else "Stayed ${hrs}h"
            }
            totalMinutes >= 1 -> "Stayed ${totalMinutes}m"
            seconds > 0 -> "Stayed ${seconds}s"
            else -> "Stay Point"
        }
    }

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}
