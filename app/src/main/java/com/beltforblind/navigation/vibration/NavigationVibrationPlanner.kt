package com.beltforblind.navigation.vibration

import com.beltforblind.route.tangent.RouteTangent
import kotlin.math.floor

enum class NavigationVibrationStatus {
    Inactive,
    RouteUnavailable,
    UnreliableLocation,
    AwaitingHeading,
    Guiding,
    OffRoute,
    Arrived,
}

data class NavigationVibrationDecision(
    val status: NavigationVibrationStatus = NavigationVibrationStatus.Inactive,
    val motorNumber: Int? = null,
    val targetBearingDegrees: Double? = null,
    val relativeBearingDegrees: Double? = null,
    val distanceToRouteMeters: Double? = null,
    val remainingDistanceMeters: Double? = null,
)

object NavigationVibrationPlanner {
    fun plan(
        tangent: RouteTangent?,
        headingDegrees: Double?,
        locationAccuracyMeters: Float?,
        previousMotorNumber: Int? = null,
    ): NavigationVibrationDecision {
        if (tangent == null) {
            return NavigationVibrationDecision(status = NavigationVibrationStatus.RouteUnavailable)
        }

        val remainingDistance = (
            tangent.totalRouteDistanceMeters - tangent.alongRouteDistanceMeters
        ).coerceAtLeast(0.0)
        val baseDecision = NavigationVibrationDecision(
            status = NavigationVibrationStatus.RouteUnavailable,
            targetBearingDegrees = tangent.tangentBearingDegrees.normalize360(),
            distanceToRouteMeters = tangent.distanceToRouteMeters,
            remainingDistanceMeters = remainingDistance,
        )
        val accuracy = locationAccuracyMeters
        if (accuracy == null || !accuracy.isFinite() || accuracy <= 0f || accuracy > MAX_GUIDANCE_ACCURACY_METERS) {
            return baseDecision.copy(status = NavigationVibrationStatus.UnreliableLocation)
        }

        val allowedRouteDistance = maxOf(
            MIN_OFF_ROUTE_DISTANCE_METERS,
            accuracy.toDouble() * ACCURACY_DISTANCE_MULTIPLIER,
        )
        if (tangent.distanceToRouteMeters > allowedRouteDistance) {
            return baseDecision.copy(status = NavigationVibrationStatus.OffRoute)
        }

        if (tangent.progress >= MIN_ARRIVAL_PROGRESS && remainingDistance <= ARRIVAL_DISTANCE_METERS) {
            return baseDecision.copy(status = NavigationVibrationStatus.Arrived)
        }

        if (headingDegrees == null || !headingDegrees.isFinite()) {
            return baseDecision.copy(status = NavigationVibrationStatus.AwaitingHeading)
        }

        val relativeBearing = (
            tangent.tangentBearingDegrees.normalize360() - headingDegrees.normalize360()
        ).normalizeSigned()
        return baseDecision.copy(
            status = NavigationVibrationStatus.Guiding,
            motorNumber = stableMotorForRelativeBearing(
                relativeBearingDegrees = relativeBearing,
                previousMotorNumber = previousMotorNumber,
            ),
            relativeBearingDegrees = relativeBearing,
        )
    }

    fun motorForRelativeBearing(relativeBearingDegrees: Double): Int {
        require(relativeBearingDegrees.isFinite()) { "Relative bearing must be finite." }
        val clockwiseBearing = relativeBearingDegrees.normalize360()
        return floor((clockwiseBearing + HALF_MOTOR_SECTOR_DEGREES) / MOTOR_SECTOR_DEGREES)
            .toInt()
            .mod(MOTOR_COUNT) + 1
    }

    fun stableMotorForRelativeBearing(
        relativeBearingDegrees: Double,
        previousMotorNumber: Int?,
    ): Int {
        require(relativeBearingDegrees.isFinite()) { "Relative bearing must be finite." }
        if (previousMotorNumber == null || previousMotorNumber !in 1..MOTOR_COUNT) {
            return motorForRelativeBearing(relativeBearingDegrees)
        }

        val previousSectorCenter = (previousMotorNumber - 1) * MOTOR_SECTOR_DEGREES
        val distanceFromPreviousCenter = (
            relativeBearingDegrees.normalize360() - previousSectorCenter
        ).normalizeSigned()
        if (kotlin.math.abs(distanceFromPreviousCenter) <= STABLE_SECTOR_HALF_WIDTH_DEGREES) {
            return previousMotorNumber
        }
        return motorForRelativeBearing(relativeBearingDegrees)
    }

    private fun Double.normalize360(): Double {
        return ((this % FULL_CIRCLE_DEGREES) + FULL_CIRCLE_DEGREES) % FULL_CIRCLE_DEGREES
    }

    private fun Double.normalizeSigned(): Double {
        val normalized = normalize360()
        return if (normalized >= HALF_CIRCLE_DEGREES) {
            normalized - FULL_CIRCLE_DEGREES
        } else {
            normalized
        }
    }

    const val MAX_GUIDANCE_ACCURACY_METERS = 25f
    const val MIN_OFF_ROUTE_DISTANCE_METERS = 30.0
    const val ARRIVAL_DISTANCE_METERS = 10.0
    const val MIN_ARRIVAL_PROGRESS = 0.95

    private const val ACCURACY_DISTANCE_MULTIPLIER = 2.0
    private const val MOTOR_COUNT = 8
    private const val MOTOR_SECTOR_DEGREES = 45.0
    private const val HALF_MOTOR_SECTOR_DEGREES = MOTOR_SECTOR_DEGREES / 2.0
    private const val MOTOR_SECTOR_HYSTERESIS_DEGREES = 8.0
    private const val STABLE_SECTOR_HALF_WIDTH_DEGREES =
        HALF_MOTOR_SECTOR_DEGREES + MOTOR_SECTOR_HYSTERESIS_DEGREES
    private const val HALF_CIRCLE_DEGREES = 180.0
    private const val FULL_CIRCLE_DEGREES = 360.0
}
