package com.beltforblind.navigation.vibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationMotorSignalPlannerTest {
    @Test
    fun guidingUsesRepeatingDirectionalPulse() {
        val pattern = NavigationMotorSignalPlanner.patternFor(
            decision = NavigationVibrationDecision(
                status = NavigationVibrationStatus.Guiding,
                motorNumber = 4,
            ),
            enabled = true,
        )!!

        assertTrue(pattern.repeats)
        assertEquals(listOf(4, null), pattern.frames.map { it.motorNumber })
    }

    @Test
    fun offRouteAlternatesLeftAndRightWarningMotors() {
        val pattern = NavigationMotorSignalPlanner.patternFor(
            decision = NavigationVibrationDecision(status = NavigationVibrationStatus.OffRoute),
            enabled = true,
        )!!

        assertTrue(pattern.repeats)
        assertEquals(listOf(7, null, 3, null), pattern.frames.map { it.motorNumber })
    }

    @Test
    fun arrivalPlaysThreeFrontPulsesOnce() {
        val pattern = NavigationMotorSignalPlanner.patternFor(
            decision = NavigationVibrationDecision(status = NavigationVibrationStatus.Arrived),
            enabled = true,
        )!!

        assertFalse(pattern.repeats)
        assertEquals(listOf(1, null, 1, null, 1, null), pattern.frames.map { it.motorNumber })
    }

    @Test
    fun unavailableInputsDoNotVibrate() {
        assertNull(
            NavigationMotorSignalPlanner.patternFor(
                decision = NavigationVibrationDecision(
                    status = NavigationVibrationStatus.UnreliableLocation,
                ),
                enabled = true,
            ),
        )
        assertNull(
            NavigationMotorSignalPlanner.patternFor(
                decision = NavigationVibrationDecision(
                    status = NavigationVibrationStatus.Guiding,
                    motorNumber = 1,
                ),
                enabled = false,
            ),
        )
    }
}
