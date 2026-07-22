package com.beltforblind.navigation.vibration

import com.beltforblind.motor.MotorArcLayout

data class MotorSignalFrame(
    val motorIntensities: List<Int>,
    val durationMillis: Long,
) {
    init {
        require(motorIntensities.size == MotorArcLayout.MOTOR_COUNT)
        require(motorIntensities.all { it in 0..255 })
        require(durationMillis > 0L)
    }

    val isStopped: Boolean
        get() = motorIntensities.all { it == 0 }
}

data class NavigationMotorSignalPattern(
    val frames: List<MotorSignalFrame>,
    val repeats: Boolean,
) {
    init {
        require(frames.isNotEmpty())
    }
}

object NavigationMotorSignalPlanner {
    fun patternFor(
        decision: NavigationVibrationDecision,
        enabled: Boolean,
    ): NavigationMotorSignalPattern? {
        if (!enabled) return null

        return when (decision.status) {
            NavigationVibrationStatus.Guiding -> decision.motorIntensities
                .takeIf { intensities -> intensities.any { it > 0 } }
                ?.let { intensities ->
                NavigationMotorSignalPattern(
                    frames = listOf(
                        MotorSignalFrame(intensities, GUIDANCE_ON_MILLIS),
                        stoppedFrame(GUIDANCE_OFF_MILLIS),
                    ),
                    repeats = true,
                )
            }
            NavigationVibrationStatus.OffRoute -> NavigationMotorSignalPattern(
                frames = listOf(
                    singleMotorFrame(LEFT_MOTOR_NUMBER, ALERT_ON_MILLIS),
                    stoppedFrame(ALERT_GAP_MILLIS),
                    singleMotorFrame(RIGHT_MOTOR_NUMBER, ALERT_ON_MILLIS),
                    stoppedFrame(OFF_ROUTE_REPEAT_GAP_MILLIS),
                ),
                repeats = true,
            )
            NavigationVibrationStatus.Arrived -> NavigationMotorSignalPattern(
                frames = buildList {
                    repeat(ARRIVAL_PULSE_COUNT) { index ->
                        add(MotorSignalFrame(FRONT_MOTOR_INTENSITIES, ARRIVAL_ON_MILLIS))
                        add(
                            stoppedFrame(
                                durationMillis = if (index == ARRIVAL_PULSE_COUNT - 1) {
                                    ARRIVAL_FINAL_OFF_MILLIS
                                } else {
                                    ARRIVAL_GAP_MILLIS
                                },
                            ),
                        )
                    }
                },
                repeats = false,
            )
            else -> null
        }
    }

    const val LEFT_MOTOR_NUMBER = 1
    const val RIGHT_MOTOR_NUMBER = 8

    private fun singleMotorFrame(motorNumber: Int, durationMillis: Long): MotorSignalFrame {
        return MotorSignalFrame(
            motorIntensities = List(MotorArcLayout.MOTOR_COUNT) { index ->
                if (index == motorNumber - 1) ALERT_INTENSITY else 0
            },
            durationMillis = durationMillis,
        )
    }

    private fun stoppedFrame(durationMillis: Long): MotorSignalFrame =
        MotorSignalFrame(
            motorIntensities = List(MotorArcLayout.MOTOR_COUNT) { 0 },
            durationMillis = durationMillis,
        )

    private val FRONT_MOTOR_INTENSITIES = MotorArcLayout.intensitiesForRelativeAngle(
        relativeAngleDegrees = 0.0,
        maximumIntensity = NavigationVibrationPlanner.GUIDANCE_MAXIMUM_INTENSITY,
    )
    private const val ALERT_INTENSITY = 128
    private const val GUIDANCE_ON_MILLIS = 320L
    private const val GUIDANCE_OFF_MILLIS = 880L
    private const val ALERT_ON_MILLIS = 220L
    private const val ALERT_GAP_MILLIS = 160L
    private const val OFF_ROUTE_REPEAT_GAP_MILLIS = 1_400L
    private const val ARRIVAL_PULSE_COUNT = 3
    private const val ARRIVAL_ON_MILLIS = 240L
    private const val ARRIVAL_GAP_MILLIS = 180L
    private const val ARRIVAL_FINAL_OFF_MILLIS = 300L
}
