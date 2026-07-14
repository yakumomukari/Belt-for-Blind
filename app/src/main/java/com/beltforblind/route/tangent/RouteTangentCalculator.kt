package com.beltforblind.route.tangent

import com.beltforblind.route.model.RoutePoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object RouteTangentCalculator {
    fun getTangent(
        routePoints: List<RoutePoint>,
        currentPoint: RoutePoint,
    ): RouteTangent? {
        if (routePoints.size < 2) {
            return null
        }

        val totalDistance = routePoints.totalDistanceMeters()
        if (totalDistance <= 0.0) {
            return null
        }

        var bestCandidate: Candidate? = null
        var distanceBeforeSegment = 0.0

        routePoints.zipWithNext().forEachIndexed { index, (start, end) ->
            val segmentLength = start.distanceToMeters(end)
            if (segmentLength <= 0.0) {
                return@forEachIndexed
            }

            val projection = projectToSegment(
                start = start,
                end = end,
                point = currentPoint,
            )
            val candidate = Candidate(
                segmentStartIndex = index,
                segmentEndIndex = index + 1,
                projectionRatio = projection.ratio,
                tangentBearingDegrees = start.bearingToDegrees(end),
                distanceToRouteMeters = projection.distanceMeters,
                alongRouteDistanceMeters = distanceBeforeSegment + segmentLength * projection.ratio,
            )

            if (
                bestCandidate == null ||
                candidate.distanceToRouteMeters < bestCandidate.distanceToRouteMeters ||
                candidate.distanceToRouteMeters == bestCandidate.distanceToRouteMeters &&
                candidate.alongRouteDistanceMeters > bestCandidate.alongRouteDistanceMeters
            ) {
                bestCandidate = candidate
            }

            distanceBeforeSegment += segmentLength
        }

        val candidate = bestCandidate ?: return null
        return RouteTangent(
            segmentStartIndex = candidate.segmentStartIndex,
            segmentEndIndex = candidate.segmentEndIndex,
            projectionRatio = candidate.projectionRatio,
            tangentBearingDegrees = candidate.tangentBearingDegrees,
            distanceToRouteMeters = candidate.distanceToRouteMeters,
            alongRouteDistanceMeters = candidate.alongRouteDistanceMeters,
            totalRouteDistanceMeters = totalDistance,
        )
    }

    fun getLatestTangent(routePoints: List<RoutePoint>): RouteTangent? {
        val currentPoint = routePoints.lastOrNull() ?: return null
        return getTangent(routePoints = routePoints, currentPoint = currentPoint)
    }

    private fun projectToSegment(
        start: RoutePoint,
        end: RoutePoint,
        point: RoutePoint,
    ): Projection {
        val originLatitudeRadians = Math.toRadians(point.latitude)
        val startXY = start.toLocalXY(originLatitudeRadians)
        val endXY = end.toLocalXY(originLatitudeRadians)
        val pointXY = point.toLocalXY(originLatitudeRadians)

        val segmentX = endXY.x - startXY.x
        val segmentY = endXY.y - startXY.y
        val segmentLengthSquared = segmentX * segmentX + segmentY * segmentY
        if (segmentLengthSquared <= 0.0) {
            return Projection(ratio = 0.0, distanceMeters = point.distanceToMeters(start))
        }

        val rawRatio = ((pointXY.x - startXY.x) * segmentX + (pointXY.y - startXY.y) * segmentY) /
            segmentLengthSquared
        val ratio = rawRatio.coerceIn(0.0, 1.0)
        val projectionX = startXY.x + segmentX * ratio
        val projectionY = startXY.y + segmentY * ratio
        val distance = sqrt((pointXY.x - projectionX).pow(2) + (pointXY.y - projectionY).pow(2))

        return Projection(ratio = ratio, distanceMeters = distance)
    }

    private fun List<RoutePoint>.totalDistanceMeters(): Double {
        return zipWithNext().sumOf { (start, end) -> start.distanceToMeters(end) }
    }

    private fun RoutePoint.distanceToMeters(other: RoutePoint): Double {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val deltaLat = Math.toRadians(other.latitude - latitude)
        val deltaLon = Math.toRadians(other.longitude - longitude)

        val a = sin(deltaLat / 2).pow(2) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    private fun RoutePoint.bearingToDegrees(other: RoutePoint): Double {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val deltaLon = Math.toRadians(other.longitude - longitude)
        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        return (Math.toDegrees(atan2(y, x)) + FULL_CIRCLE_DEGREES) % FULL_CIRCLE_DEGREES
    }

    private fun RoutePoint.toLocalXY(originLatitudeRadians: Double): XY {
        return XY(
            x = Math.toRadians(longitude) * cos(originLatitudeRadians) * EARTH_RADIUS_METERS,
            y = Math.toRadians(latitude) * EARTH_RADIUS_METERS,
        )
    }

    private data class XY(
        val x: Double,
        val y: Double,
    )

    private data class Projection(
        val ratio: Double,
        val distanceMeters: Double,
    )

    private data class Candidate(
        val segmentStartIndex: Int,
        val segmentEndIndex: Int,
        val projectionRatio: Double,
        val tangentBearingDegrees: Double,
        val distanceToRouteMeters: Double,
        val alongRouteDistanceMeters: Double,
    )

    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val FULL_CIRCLE_DEGREES = 360.0
}
