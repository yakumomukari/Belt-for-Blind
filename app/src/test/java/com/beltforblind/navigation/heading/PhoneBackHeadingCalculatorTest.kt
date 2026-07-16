package com.beltforblind.navigation.heading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneBackHeadingCalculatorTest {
    @Test
    fun reportsNorthWhenPhoneBackFacesNorth() {
        assertHeading(expected = 0.0, forwardEast = 0f, forwardNorth = 1f)
    }

    @Test
    fun reportsEastWhenPhoneBackFacesEast() {
        assertHeading(expected = 90.0, forwardEast = 1f, forwardNorth = 0f)
    }

    @Test
    fun reportsSouthWhenPhoneBackFacesSouth() {
        assertHeading(expected = 180.0, forwardEast = 0f, forwardNorth = -1f)
    }

    @Test
    fun reportsWestWhenPhoneBackFacesWest() {
        assertHeading(expected = 270.0, forwardEast = -1f, forwardNorth = 0f)
    }

    @Test
    fun rejectsOrientationWhenPhoneBackDoesNotHaveHorizontalProjection() {
        val matrix = FloatArray(9)

        assertNull(PhoneBackHeadingCalculator.headingFromRotationMatrix(matrix))
    }

    @Test
    fun convertsMagneticHeadingToTrueNorthAndWrapsAt360() {
        assertEquals(
            5.0,
            PhoneBackHeadingCalculator.toTrueNorth(
                magneticHeadingDegrees = 355.0,
                declinationDegrees = 10.0,
            ),
            0.001,
        )
    }

    private fun assertHeading(
        expected: Double,
        forwardEast: Float,
        forwardNorth: Float,
    ) {
        val matrix = FloatArray(9).apply {
            this[2] = -forwardEast
            this[5] = -forwardNorth
        }

        assertEquals(
            expected,
            PhoneBackHeadingCalculator.headingFromRotationMatrix(matrix)!!,
            0.001,
        )
    }
}
