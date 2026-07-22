package com.beltforblind.navigation.vibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationMotorSignalPlannerTest {
    @Test
    fun guidingUsesRepeatingIntensityVectorPulse() {
        val intensities = listOf(0, 0, 0, 80, 48, 0, 0, 0)
        val pattern = NavigationMotorSignalPlanner.patternFor(
            decision = NavigationVibrationDecision(
                status = NavigationVibrationStatus.Guiding,
                motorNumber = 4,
                motorIntensities = intensities,
            ),
            enabled = true,
        )!!

        assertTrue(pattern.repeats)
        assertEquals(intensities, pattern.frames[0].motorIntensities)
        assertTrue(pattern.frames[1].isStopped)
    }

    @Test
    fun offRouteAlternatesLeftAndRightEndMotors() {
        val pattern = NavigationMotorSignalPlanner.patternFor(
            decision = NavigationVibrationDecision(status = NavigationVibrationStatus.OffRoute),
            enabled = true,
        )!!

        assertTrue(pattern.repeats)
        assertEquals(
            listOf(listOf(1), emptyList(), listOf(8), emptyList()),
            pattern.frames.map(::activeMotors),
        )
    }

    @Test
    fun arrivalPlaysThreeCentralPairPulsesOnce() {
        val pattern = NavigationMotorSignalPlanner.patternFor(
            decision = NavigationVibrationDecision(status = NavigationVibrationStatus.Arrived),
            enabled = true,
        )!!

        assertFalse(pattern.repeats)
        assertEquals(
            listOf(
                listOf(4, 5),
                emptyList(),
                listOf(4, 5),
                emptyList(),
                listOf(4, 5),
                emptyList(),
            ),
            pattern.frames.map(::activeMotors),
        )
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
                    motorNumber = 4,
                    motorIntensities = listOf(0, 0, 0, 64, 64, 0, 0, 0),
                ),
                enabled = false,
            ),
        )
    }

    private fun activeMotors(frame: MotorSignalFrame): List<Int> =
        frame.motorIntensities.mapIndexedNotNull { index, intensity ->
            (index + 1).takeIf { intensity > 0 }
        }
}
