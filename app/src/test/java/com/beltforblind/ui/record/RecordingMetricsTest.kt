package com.beltforblind.ui.record

import com.beltforblind.route.model.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingMetricsTest {
    @Test
    fun distanceAddsAcceptedPathSegments() {
        val points = listOf(
            point(0.0),
            point(0.001),
            point(0.002),
        )

        assertEquals(222.4, points.totalRecordedDistanceMeters(), 1.0)
    }

    @Test
    fun averageSpeedUsesDistanceAndElapsedTime() {
        assertEquals(9.0, averageRecordedSpeedKmh(1_000.0, 400L), 0.01)
        assertEquals(0.0, averageRecordedSpeedKmh(100.0, 0L), 0.0)
    }

    private fun point(longitude: Double): RoutePoint {
        return RoutePoint(
            latitude = 0.0,
            longitude = longitude,
            timestamp = 1L,
            accuracy = 3f,
        )
    }
}
