package com.beltforblind.motor

import kotlin.math.floor
import kotlin.math.roundToInt

object MotorArcLayout {
    const val MOTOR_COUNT = 8
    const val MOTOR_SPACING_DEGREES = 22.0
    const val MIN_RELATIVE_ANGLE_DEGREES = -77.0
    const val MAX_RELATIVE_ANGLE_DEGREES = 77.0

    val relativeAnglesDegrees: List<Double> =
        List(MOTOR_COUNT) { index ->
            MIN_RELATIVE_ANGLE_DEGREES + index * MOTOR_SPACING_DEGREES
        }

    fun motorForRelativeAngle(relativeAngleDegrees: Double): Int {
        require(relativeAngleDegrees.isFinite()) { "Relative angle must be finite." }
        val clamped = relativeAngleDegrees.coerceIn(
            MIN_RELATIVE_ANGLE_DEGREES,
            MAX_RELATIVE_ANGLE_DEGREES,
        )
        return (
            (clamped - MIN_RELATIVE_ANGLE_DEGREES) / MOTOR_SPACING_DEGREES
            ).roundToInt() + 1
    }

    fun relativeAngleForMotor(motorNumber: Int): Double {
        require(motorNumber in 1..MOTOR_COUNT) {
            "Motor number must be between 1 and $MOTOR_COUNT."
        }
        return relativeAnglesDegrees[motorNumber - 1]
    }

    fun intensitiesForRelativeAngle(
        relativeAngleDegrees: Double,
        maximumIntensity: Int,
    ): List<Int> {
        require(relativeAngleDegrees.isFinite()) { "Relative angle must be finite." }
        require(maximumIntensity in 0..255) { "Maximum intensity must be between 0 and 255." }

        val clamped = relativeAngleDegrees.coerceIn(
            MIN_RELATIVE_ANGLE_DEGREES,
            MAX_RELATIVE_ANGLE_DEGREES,
        )
        val position = (clamped - MIN_RELATIVE_ANGLE_DEGREES) / MOTOR_SPACING_DEGREES
        val lowerIndex = floor(position).toInt().coerceIn(0, MOTOR_COUNT - 1)
        val upperIndex = (lowerIndex + 1).coerceAtMost(MOTOR_COUNT - 1)
        if (lowerIndex == upperIndex) {
            return List(MOTOR_COUNT) { index ->
                maximumIntensity.takeIf { index == lowerIndex } ?: 0
            }
        }

        val upperWeight = position - lowerIndex
        val upperIntensity = (maximumIntensity * upperWeight).roundToInt()
        val lowerIntensity = maximumIntensity - upperIntensity
        return List(MOTOR_COUNT) { index ->
            when (index) {
                lowerIndex -> lowerIntensity
                upperIndex -> upperIntensity
                else -> 0
            }
        }
    }
}
