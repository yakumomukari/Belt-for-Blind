package com.beltforblind.route.recorder

import com.beltforblind.route.location.LocationDataSource
import com.beltforblind.route.model.RoutePoint
import com.beltforblind.route.model.RouteRecord
import com.beltforblind.route.storage.RouteStore
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteRecordingManagerTest {
    @Test
    fun pauseStopsCollectionAndResumeKeepsExistingPoints() {
        var now = 1_000L
        val location = FakeLocationDataSource()
        val recorder = RouteRecordingManager(
            locationDataSource = location,
            routeStore = InMemoryRouteStore(),
            currentTimeMillis = { now },
        )

        recorder.startRecord()
        now += 16_000L
        location.emit(point(longitude = 121.0, timestamp = now))
        assertEquals(1, recorder.getPointCount())

        recorder.pauseRecord()
        location.emit(point(longitude = 121.1, timestamp = now + 1_000L))
        assertEquals(1, recorder.getPointCount())
        assertEquals(1, location.stopCount)

        recorder.resumeRecord()
        location.emit(point(longitude = 121.2, timestamp = now + 2_000L))
        assertEquals(2, recorder.getPointCount())
        assertEquals(2, location.startCount)
    }

    private fun point(longitude: Double, timestamp: Long): RoutePoint {
        return RoutePoint(
            latitude = 31.0,
            longitude = longitude,
            timestamp = timestamp,
            accuracy = 3f,
        )
    }
}

private class FakeLocationDataSource : LocationDataSource {
    private var listener: ((RoutePoint) -> Unit)? = null
    var startCount = 0
        private set
    var stopCount = 0
        private set

    override fun start(onPoint: (RoutePoint) -> Unit) {
        startCount += 1
        listener = onPoint
    }

    override fun stop() {
        stopCount += 1
    }

    fun emit(point: RoutePoint) {
        listener?.invoke(point)
    }
}

private class InMemoryRouteStore : RouteStore {
    private val routes = mutableListOf<RouteRecord>()

    override fun save(route: RouteRecord): RouteRecord {
        routes += route
        return route
    }

    override fun loadAll(): List<RouteRecord> = routes.toList()

    override fun delete(routeId: String): Boolean = routes.removeAll { it.id == routeId }
}
