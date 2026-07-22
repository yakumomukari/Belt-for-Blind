package com.beltforblind.navigation.vibration

import com.beltforblind.motor.MotorArcLayout
import com.beltforblind.route.tangent.RouteTangent

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
    val motorIntensities: List<Int> = List(MotorArcLayout.MOTOR_COUNT) { 0 },
    val targetBearingDegrees: Double? = null,
    val relativeBearingDegrees: Double? = null,
    val guidanceAngleDegrees: Double? = null,
    val distanceToRouteMeters: Double? = null,
    val remainingDistanceMeters: Double? = null,
)

object NavigationVibrationPlanner {
    fun plan(
        tangent: RouteTangent?,
        headingDegrees: Double?,
        locationAccuracyMeters: Float?,
        previousGuidanceAngleDegrees: Double? = null,
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
        val guidanceAngle = stableGuidanceAngle(
            relativeBearingDegrees = relativeBearing,
            previousGuidanceAngleDegrees = previousGuidanceAngleDegrees,
        )
        val intensities = MotorArcLayout.intensitiesForRelativeAngle(
            relativeAngleDegrees = guidanceAngle,
            maximumIntensity = GUIDANCE_MAXIMUM_INTENSITY,
        )
        return baseDecision.copy(
            status = NavigationVibrationStatus.Guiding,
            motorNumber = strongestMotor(intensities),
            motorIntensities = intensities,
            relativeBearingDegrees = relativeBearing,
            guidanceAngleDegrees = guidanceAngle,
        )
    }

    fun stableGuidanceAngle(
        relativeBearingDegrees: Double,
        previousGuidanceAngleDegrees: Double?,
    ): Double {
        require(relativeBearingDegrees.isFinite()) { "Relative bearing must be finite." }
        val normalized = relativeBearingDegrees.normalizeSigned()
        val previous = previousGuidanceAngleDegrees
            ?.takeIf { it.isFinite() }
            ?.coerceIn(
                MotorArcLayout.MIN_RELATIVE_ANGLE_DEGREES,
                MotorArcLayout.MAX_RELATIVE_ANGLE_DEGREES,
            )
        if (
            kotlin.math.abs(normalized) >= REAR_SIDE_HOLD_START_DEGREES &&
            previous != null &&
            kotlin.math.abs(previous) >= REAR_SIDE_HOLD_PREVIOUS_MIN_DEGREES
        ) {
            return if (previous < 0.0) {
                MotorArcLayout.MIN_RELATIVE_ANGLE_DEGREES
            } else {
                MotorArcLayout.MAX_RELATIVE_ANGLE_DEGREES
            }
        }
        val clamped = normalized.coerceIn(
            MotorArcLayout.MIN_RELATIVE_ANGLE_DEGREES,
            MotorArcLayout.MAX_RELATIVE_ANGLE_DEGREES,
        )
        if (previous != null && kotlin.math.abs(clamped - previous) < GUIDANCE_DEADBAND_DEGREES) {
            return previous
        }
        return clamped
    }

    private fun strongestMotor(intensities: List<Int>): Int? =
        intensities.withIndex()
            .filter { it.value > 0 }
            .maxByOrNull { it.value }
            ?.index
            ?.plus(1)

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
    const val GUIDANCE_MAXIMUM_INTENSITY = 128

    private const val ACCURACY_DISTANCE_MULTIPLIER = 2.0
    private const val GUIDANCE_DEADBAND_DEGREES = 3.0
    private const val REAR_SIDE_HOLD_START_DEGREES = 170.0
    private const val REAR_SIDE_HOLD_PREVIOUS_MIN_DEGREES = 70.0
    private const val HALF_CIRCLE_DEGREES = 180.0
    private const val FULL_CIRCLE_DEGREES = 360.0
}
