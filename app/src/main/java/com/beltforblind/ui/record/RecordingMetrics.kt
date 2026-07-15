package com.beltforblind.ui.record

import com.beltforblind.route.model.RoutePoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal fun List<RoutePoint>.totalRecordedDistanceMeters(): Double {
    return zipWithNext().sumOf { (start, end) -> start.distanceToMeters(end) }
}

internal fun averageRecordedSpeedKmh(distanceMeters: Double, elapsedTimeSeconds: Long): Double {
    if (elapsedTimeSeconds <= 0L) return 0.0
    return distanceMeters / elapsedTimeSeconds * 3.6
}

private fun RoutePoint.distanceToMeters(other: RoutePoint): Double {
    val startLatitude = Math.toRadians(latitude)
    val endLatitude = Math.toRadians(other.latitude)
    val latitudeDelta = Math.toRadians(other.latitude - latitude)
    val longitudeDelta = Math.toRadians(other.longitude - longitude)
    val haversine = sin(latitudeDelta / 2).pow(2) +
        cos(startLatitude) * cos(endLatitude) * sin(longitudeDelta / 2).pow(2)
    return EARTH_RADIUS_METERS * 2 * atan2(
        sqrt(haversine.coerceIn(0.0, 1.0)),
        sqrt((1.0 - haversine).coerceIn(0.0, 1.0)),
    )
}

private const val EARTH_RADIUS_METERS = 6_371_000.0
