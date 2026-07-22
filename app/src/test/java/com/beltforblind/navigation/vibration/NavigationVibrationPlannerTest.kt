package com.beltforblind.navigation.vibration

import com.beltforblind.route.tangent.RouteTangent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationVibrationPlannerTest {
    @Test
    fun mapsFrontDirectionToTwoCentralMotors() {
        val decision = plan(targetBearing = 0.0, heading = 0.0)

        assertEquals(0.0, decision.guidanceAngleDegrees!!, 0.001)
        assertEquals(listOf(0, 0, 0, 64, 64, 0, 0, 0), decision.motorIntensities)
    }

    @Test
    fun interpolatesStrengthBetweenAdjacentMotors() {
        val decision = plan(targetBearing = 20.0, heading = 0.0)

        assertEquals(20.0, decision.relativeBearingDegrees!!, 0.001)
        assertEquals(listOf(0, 0, 0, 0, 76, 52, 0, 0), decision.motorIntensities)
        assertEquals(5, decision.motorNumber)
    }

    @Test
    fun clampsDirectionsOutsideFrontArcToEndMotors() {
        assertEquals(
            listOf(128, 0, 0, 0, 0, 0, 0, 0),
            plan(targetBearing = 270.0, heading = 0.0).motorIntensities,
        )
        assertEquals(
            listOf(0, 0, 0, 0, 0, 0, 0, 128),
            plan(targetBearing = 90.0, heading = 0.0).motorIntensities,
        )
    }

    @Test
    fun keepsPreviousAngleInsideThreeDegreeDeadband() {
        val angle = NavigationVibrationPlanner.stableGuidanceAngle(
            relativeBearingDegrees = 12.0,
            previousGuidanceAngleDegrees = 10.0,
        )

        assertEquals(10.0, angle, 0.001)
    }

    @Test
    fun updatesAngleAfterLeavingDeadband() {
        val angle = NavigationVibrationPlanner.stableGuidanceAngle(
            relativeBearingDegrees = 14.0,
            previousGuidanceAngleDegrees = 10.0,
        )

        assertEquals(14.0, angle, 0.001)
    }

    @Test
    fun keepsPreviousTurnSideAcrossRearBearingWrap() {
        val angle = NavigationVibrationPlanner.stableGuidanceAngle(
            relativeBearingDegrees = -179.0,
            previousGuidanceAngleDegrees = 77.0,
        )

        assertEquals(77.0, angle, 0.001)
    }

    @Test
    fun suppressesMotorsWhenLocationIsUnreliable() {
        val decision = NavigationVibrationPlanner.plan(
            tangent = tangent(),
            headingDegrees = 0.0,
            locationAccuracyMeters = 30f,
        )

        assertEquals(NavigationVibrationStatus.UnreliableLocation, decision.status)
        assertNull(decision.motorNumber)
        assertEquals(List(8) { 0 }, decision.motorIntensities)
    }

    @Test
    fun reportsOffRouteAndArrivalWithoutDirectionOutput() {
        val offRoute = NavigationVibrationPlanner.plan(
            tangent = tangent(distanceToRoute = 40.0),
            headingDegrees = 0.0,
            locationAccuracyMeters = 3f,
        )
        val arrived = NavigationVibrationPlanner.plan(
            tangent = tangent(alongRouteDistance = 995.0),
            headingDegrees = 0.0,
            locationAccuracyMeters = 3f,
        )

        assertEquals(NavigationVibrationStatus.OffRoute, offRoute.status)
        assertEquals(List(8) { 0 }, offRoute.motorIntensities)
        assertEquals(NavigationVibrationStatus.Arrived, arrived.status)
        assertEquals(5.0, arrived.remainingDistanceMeters!!, 0.001)
        assertEquals(List(8) { 0 }, arrived.motorIntensities)
    }

    @Test
    fun waitsForHeadingBeforeSelectingMotors() {
        val decision = NavigationVibrationPlanner.plan(
            tangent = tangent(),
            headingDegrees = null,
            locationAccuracyMeters = 3f,
        )

        assertEquals(NavigationVibrationStatus.AwaitingHeading, decision.status)
        assertNull(decision.motorNumber)
        assertEquals(List(8) { 0 }, decision.motorIntensities)
    }

    @Test
    fun selectsExpectedArcSideForNorthRouteAtCardinalHeadings() {
        assertEquals(listOf(4, 5), activeMotors(plan(0.0, 0.0)))
        assertEquals(listOf(1), activeMotors(plan(0.0, 90.0)))
        assertEquals(listOf(1), activeMotors(plan(0.0, 180.0)))
        assertEquals(listOf(8), activeMotors(plan(0.0, 270.0)))
    }

    private fun activeMotors(decision: NavigationVibrationDecision): List<Int> =
        decision.motorIntensities.mapIndexedNotNull { index, intensity ->
            (index + 1).takeIf { intensity > 0 }
        }

    private fun plan(
        targetBearing: Double,
        heading: Double,
        previousGuidanceAngle: Double? = null,
    ): NavigationVibrationDecision {
        return NavigationVibrationPlanner.plan(
            tangent = tangent(targetBearing = targetBearing),
            headingDegrees = heading,
            locationAccuracyMeters = 3f,
            previousGuidanceAngleDegrees = previousGuidanceAngle,
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
