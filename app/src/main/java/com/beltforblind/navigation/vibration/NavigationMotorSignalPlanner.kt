package com.beltforblind.navigation.vibration

data class MotorSignalFrame(
    val motorNumber: Int?,
    val durationMillis: Long,
) {
    init {
        require(motorNumber == null || motorNumber in 1..8)
        require(durationMillis > 0L)
    }
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
            NavigationVibrationStatus.Guiding -> decision.motorNumber?.let { motorNumber ->
                NavigationMotorSignalPattern(
                    frames = listOf(
                        MotorSignalFrame(motorNumber, GUIDANCE_ON_MILLIS),
                        MotorSignalFrame(null, GUIDANCE_OFF_MILLIS),
                    ),
                    repeats = true,
                )
            }
            NavigationVibrationStatus.OffRoute -> NavigationMotorSignalPattern(
                frames = listOf(
                    MotorSignalFrame(LEFT_MOTOR_NUMBER, ALERT_ON_MILLIS),
                    MotorSignalFrame(null, ALERT_GAP_MILLIS),
                    MotorSignalFrame(RIGHT_MOTOR_NUMBER, ALERT_ON_MILLIS),
                    MotorSignalFrame(null, OFF_ROUTE_REPEAT_GAP_MILLIS),
                ),
                repeats = true,
            )
            NavigationVibrationStatus.Arrived -> NavigationMotorSignalPattern(
                frames = buildList {
                    repeat(ARRIVAL_PULSE_COUNT) { index ->
                        add(MotorSignalFrame(FRONT_MOTOR_NUMBER, ARRIVAL_ON_MILLIS))
                        add(
                            MotorSignalFrame(
                                motorNumber = null,
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

    const val FRONT_MOTOR_NUMBER = 1
    const val RIGHT_MOTOR_NUMBER = 3
    const val LEFT_MOTOR_NUMBER = 7

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
