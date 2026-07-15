package com.beltforblind.ui.sport

import com.beltforblind.route.model.RoutePoint
import com.beltforblind.route.model.RouteRecord
import com.beltforblind.route.tangent.RouteTangentCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteMetricsTest {
    @Test
    fun distanceAddsEachRouteSegment() {
        val route = routeWith(
            point(0.0, 0.0),
            point(0.0, 0.001),
            point(0.0, 0.002),
        )

        assertEquals(222.4, route.totalDistanceMeters(), 1.0)
    }

    @Test
    fun startRequiresUsableRoutePermissionAndLocation() {
        val route = routeWith(point(39.0, 116.0), point(39.001, 116.001))
        val location = point(39.0, 116.0)

        assertFalse(SportUiState(selectedRoute = route).canStart)
        assertFalse(
            SportUiState(
                selectedRoute = route,
                locationPermissionGranted = true,
            ).canStart,
        )
        assertTrue(
            SportUiState(
                selectedRoute = route,
                locationPermissionGranted = true,
                currentLocation = location,
            ).canStart,
        )
    }

    @Test
    fun tangentProjectionReportsProgressAlongRoute() {
        val route = routeWith(point(0.0, 0.0), point(0.0, 0.002))

        val tangent = RouteTangentCalculator.getTangent(
            routePoints = route.points,
            currentPoint = point(0.0, 0.001),
        )

        requireNotNull(tangent)
        assertEquals(0.5, tangent.progress, 0.01)
        assertEquals(0.0, tangent.distanceToRouteMeters, 0.1)
    }

    private fun routeWith(vararg points: RoutePoint): RouteRecord {
        return RouteRecord(
            id = "route-test",
            name = "Test route",
            createdAt = 1L,
            points = points.toList(),
        )
    }

    private fun point(latitude: Double, longitude: Double): RoutePoint {
        return RoutePoint(latitude, longitude, 1L, 3f)
    }
}
