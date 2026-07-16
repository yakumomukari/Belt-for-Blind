package com.beltforblind.navigation.vibration

import com.beltforblind.route.tangent.RouteTangent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationVibrationPlannerTest {
    @Test
    fun mapsClockwiseDirectionsToExistingMotorWheel() {
        assertEquals(1, NavigationVibrationPlanner.motorForRelativeBearing(0.0))
        assertEquals(2, NavigationVibrationPlanner.motorForRelativeBearing(45.0))
        assertEquals(3, NavigationVibrationPlanner.motorForRelativeBearing(90.0))
        assertEquals(5, NavigationVibrationPlanner.motorForRelativeBearing(180.0))
        assertEquals(7, NavigationVibrationPlanner.motorForRelativeBearing(-90.0))
        assertEquals(8, NavigationVibrationPlanner.motorForRelativeBearing(-45.0))
    }

    @Test
    fun handlesNorthWrapAroundWithoutChangingMotor() {
        val decision = plan(targetBearing = 10.0, heading = 350.0)

        assertEquals(NavigationVibrationStatus.Guiding, decision.status)
        assertEquals(1, decision.motorNumber)
        assertEquals(20.0, decision.relativeBearingDegrees!!, 0.001)
    }

    @Test
    fun mapsDirectionAcrossSectorBoundary() {
        val decision = plan(targetBearing = 23.0, heading = 0.0)

        assertEquals(2, decision.motorNumber)
    }

    @Test
    fun suppressesMotorWhenLocationIsUnreliable() {
        val decision = NavigationVibrationPlanner.plan(
            tangent = tangent(),
            headingDegrees = 0.0,
            locationAccuracyMeters = 30f,
        )

        assertEquals(NavigationVibrationStatus.UnreliableLocation, decision.status)
        assertNull(decision.motorNumber)
    }

    @Test
    fun suppressesMotorWhenUserIsOffRoute() {
        val decision = NavigationVibrationPlanner.plan(
            tangent = tangent(distanceToRoute = 40.0),
            headingDegrees = 0.0,
            locationAccuracyMeters = 3f,
        )

        assertEquals(NavigationVibrationStatus.OffRoute, decision.status)
        assertNull(decision.motorNumber)
    }

    @Test
    fun reportsArrivalNearRouteEnd() {
        val decision = NavigationVibrationPlanner.plan(
            tangent = tangent(alongRouteDistance = 995.0),
            headingDegrees = 0.0,
            locationAccuracyMeters = 3f,
        )

        assertEquals(NavigationVibrationStatus.Arrived, decision.status)
        assertNull(decision.motorNumber)
        assertEquals(5.0, decision.remainingDistanceMeters!!, 0.001)
    }

    @Test
    fun waitsForHeadingBeforeSelectingMotor() {
        val decision = NavigationVibrationPlanner.plan(
            tangent = tangent(),
            headingDegrees = null,
            locationAccuracyMeters = 3f,
        )

        assertEquals(NavigationVibrationStatus.AwaitingHeading, decision.status)
        assertNull(decision.motorNumber)
    }

    @Test
    fun selectsExpectedMotorForNorthRouteAtCardinalBodyHeadings() {
        assertEquals(1, plan(targetBearing = 0.0, heading = 0.0).motorNumber)
        assertEquals(7, plan(targetBearing = 0.0, heading = 90.0).motorNumber)
        assertEquals(5, plan(targetBearing = 0.0, heading = 180.0).motorNumber)
        assertEquals(3, plan(targetBearing = 0.0, heading = 270.0).motorNumber)
    }

    private fun plan(targetBearing: Double, heading: Double): NavigationVibrationDecision {
        return NavigationVibrationPlanner.plan(
            tangent = tangent(targetBearing = targetBearing),
            headingDegrees = heading,
            locationAccuracyMeters = 3f,
        )
    }

    private fun tangent(
        targetBearing: Double = 0.0,
        distanceToRoute: Double = 0.0,
        alongRouteDistance: Double = 500.0,
    ): RouteTangent {
        return RouteTangent(
            segmentStartIndex = 0,
            segmentEndIndex = 1,
            projectionRatio = 0.5,
            tangentBearingDegrees = targetBearing,
            distanceToRouteMeters = distanceToRoute,
            alongRouteDistanceMeters = alongRouteDistance,
            totalRouteDistanceMeters = 1_000.0,
        )
    }
}
