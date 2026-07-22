package com.beltforblind.ui.sport

import com.beltforblind.motor.MotorArcLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotorArcLayoutTest {
    @Test
    fun relativeAngles_areSymmetricWithTwentyTwoDegreeSpacing() {
        assertEquals(
            listOf(-77.0, -55.0, -33.0, -11.0, 11.0, 33.0, 55.0, 77.0),
            MotorArcLayout.relativeAnglesDegrees,
        )
    }

    @Test
    fun motorForRelativeAngle_selectsNearestMotorAndClampsEnds() {
        assertEquals(1, MotorArcLayout.motorForRelativeAngle(-100.0))
        assertEquals(1, MotorArcLayout.motorForRelativeAngle(-77.0))
        assertEquals(4, MotorArcLayout.motorForRelativeAngle(-1.0))
        assertEquals(5, MotorArcLayout.motorForRelativeAngle(1.0))
        assertEquals(8, MotorArcLayout.motorForRelativeAngle(77.0))
        assertEquals(8, MotorArcLayout.motorForRelativeAngle(100.0))
    }

    @Test
    fun motorAngles_roundTrip() {
        for (motor in 1..MotorArcLayout.MOTOR_COUNT) {
            assertEquals(
                motor,
                MotorArcLayout.motorForRelativeAngle(
                    MotorArcLayout.relativeAngleForMotor(motor),
                ),
            )
        }
    }

    @Test
    fun intensityBlend_usesOneMotorAtItsExactAngle() {
        assertEquals(
            listOf(0, 0, 0, 200, 0, 0, 0, 0),
            MotorArcLayout.intensitiesForRelativeAngle(-11.0, 200),
        )
    }

    @Test
    fun intensityBlend_splitsStrengthAtFrontAxis() {
        assertEquals(
            listOf(0, 0, 0, 100, 100, 0, 0, 0),
            MotorArcLayout.intensitiesForRelativeAngle(0.0, 200),
        )
    }

    @Test
    fun intensityBlend_clampsOutsideArcToEndMotors() {
        assertEquals(
            listOf(180, 0, 0, 0, 0, 0, 0, 0),
            MotorArcLayout.intensitiesForRelativeAngle(-180.0, 180),
        )
        assertEquals(
            listOf(0, 0, 0, 0, 0, 0, 0, 180),
            MotorArcLayout.intensitiesForRelativeAngle(180.0, 180),
        )
    }

    @Test
    fun intensityBlend_keepsConstantBudgetAndAtMostTwoActiveMotors() {
        for (angle in -180..180) {
            val intensities = MotorArcLayout.intensitiesForRelativeAngle(
                relativeAngleDegrees = angle.toDouble(),
                maximumIntensity = 128,
            )

            assertEquals("angle=$angle", 128, intensities.sum())
            assertTrue("angle=$angle", intensities.count { it > 0 } <= 2)
        }
    }
}
