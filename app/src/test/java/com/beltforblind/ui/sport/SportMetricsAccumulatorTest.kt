package com.beltforblind.ui.sport

import com.beltforblind.route.model.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SportMetricsAccumulatorTest {
    @Test
    fun accumulatesDistanceAndRollingPaceFromUsablePoints() {
        val accumulator = SportMetricsAccumulator()
        accumulator.reset(point(longitude = 0.0, timestamp = 0L))

        accumulator.add(point(longitude = 0.0001, timestamp = 3_000L))
        val snapshot = accumulator.add(point(longitude = 0.0002, timestamp = 6_000L))

        assertEquals(22.2, snapshot.distanceMeters, 1.0)
        assertTrue(snapshot.paceSecondsPerKilometer in 250L..290L)
    }

    @Test
    fun ignoresPointWithPoorAccuracy() {
        val accumulator = SportMetricsAccumulator()
        accumulator.reset(point(longitude = 0.0, timestamp = 0L))

        val snapshot = accumulator.add(
            point(longitude = 0.0001, timestamp = 3_000L, accuracy = 50f),
        )

        assertEquals(0.0, snapshot.distanceMeters, 0.0)
        assertNull(snapshot.paceSecondsPerKilometer)
    }

    @Test
    fun ignoresImplausibleLocationJump() {
        val accumulator = SportMetricsAccumulator()
        accumulator.reset(point(longitude = 0.0, timestamp = 0L))

        val snapshot = accumulator.add(point(longitude = 1.0, timestamp = 3_000L))

        assertEquals(0.0, snapshot.distanceMeters, 0.0)
        assertNull(snapshot.paceSecondsPerKilometer)
    }

    @Test
    fun resumeUsesCurrentPointAsNewDistanceBaseline() {
        val accumulator = SportMetricsAccumulator()
        accumulator.reset(point(longitude = 0.0, timestamp = 0L))
        accumulator.add(point(longitude = 0.0001, timestamp = 3_000L))

        accumulator.pause()
        accumulator.resumeAt(point(longitude = 1.0, timestamp = 60_000L))
        val snapshot = accumulator.add(point(longitude = 1.0001, timestamp = 63_000L))

        assertEquals(22.2, snapshot.distanceMeters, 1.0)
    }

    private fun point(
        longitude: Double,
        timestamp: Long,
        accuracy: Float = 3f,
    ): RoutePoint {
        return RoutePoint(
            latitude = 0.0,
            longitude = longitude,
            timestamp = timestamp,
            accuracy = accuracy,
        )
    }
}
