package com.beltforblind.ui.sport

import com.beltforblind.route.model.RoutePoint
import java.util.ArrayDeque
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal data class SportMetricsSnapshot(
    val distanceMeters: Double,
    val paceSecondsPerKilometer: Long?,
)

internal class SportMetricsAccumulator {
    private val recentSegments = ArrayDeque<MovementSegment>()
    private var lastPoint: RoutePoint? = null
    private var totalDistanceMeters = 0.0

    fun reset(startPoint: RoutePoint?) {
        recentSegments.clear()
        totalDistanceMeters = 0.0
        lastPoint = startPoint?.takeIf { it.hasUsableAccuracy() }
    }

    fun pause() {
        lastPoint = null
        recentSegments.clear()
    }

    fun resumeAt(point: RoutePoint?) {
        lastPoint = point?.takeIf { it.hasUsableAccuracy() }
        recentSegments.clear()
    }

    fun add(point: RoutePoint): SportMetricsSnapshot {
        if (!point.hasUsableAccuracy()) return snapshot()

        val previous = lastPoint
        lastPoint = point
        if (previous == null) return snapshot()

        val durationSeconds = (point.timestamp - previous.timestamp) / 1_000.0
        if (durationSeconds <= 0.0 || durationSeconds > MAX_SEGMENT_DURATION_SECONDS) {
            return snapshot()
        }

        val distance = previous.distanceToMeters(point)
        val maximumDistance = MAX_RUNNING_SPEED_METERS_PER_SECOND * durationSeconds
        if (distance < MIN_MOVEMENT_METERS || distance > maximumDistance) {
            return snapshot()
        }

        totalDistanceMeters += distance
        recentSegments.addLast(
            MovementSegment(
                endTimestamp = point.timestamp,
                distanceMeters = distance,
                durationSeconds = durationSeconds,
            ),
        )
        trimRecentSegments(point.timestamp)
        return snapshot()
    }

    private fun trimRecentSegments(now: Long) {
        while (
            recentSegments.isNotEmpty() &&
            now - recentSegments.first().endTimestamp > PACE_WINDOW_MILLIS
        ) {
            recentSegments.removeFirst()
        }
    }

    private fun snapshot(): SportMetricsSnapshot {
        val recentDistance = recentSegments.sumOf(MovementSegment::distanceMeters)
        val recentDuration = recentSegments.sumOf(MovementSegment::durationSeconds)
        val pace = if (recentDistance >= MIN_PACE_DISTANCE_METERS) {
            (recentDuration / recentDistance * 1_000.0).toLong()
        } else {
            null
        }
        return SportMetricsSnapshot(totalDistanceMeters, pace)
    }

    private fun RoutePoint.hasUsableAccuracy(): Boolean {
        return accuracy != null && accuracy > 0f && accuracy <= MAX_ACTIVITY_ACCURACY_METERS
    }

    private fun RoutePoint.distanceToMeters(other: RoutePoint): Double {
        val startLatitude = Math.toRadians(latitude)
        val endLatitude = Math.toRadians(other.latitude)
        val latitudeDelta = Math.toRadians(other.latitude - latitude)
        val longitudeDelta = Math.toRadians(other.longitude - longitude)
        val a = sin(latitudeDelta / 2).pow(2) +
            cos(startLatitude) * cos(endLatitude) * sin(longitudeDelta / 2).pow(2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private data class MovementSegment(
        val endTimestamp: Long,
        val distanceMeters: Double,
        val durationSeconds: Double,
    )

    private companion object {
        const val MAX_ACTIVITY_ACCURACY_METERS = 25f
        const val MAX_SEGMENT_DURATION_SECONDS = 30.0
        const val MAX_RUNNING_SPEED_METERS_PER_SECOND = 12.0
        const val MIN_MOVEMENT_METERS = 2.0
        const val MIN_PACE_DISTANCE_METERS = 10.0
        const val PACE_WINDOW_MILLIS = 60_000L
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
