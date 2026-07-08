package com.beltforblind.ui.record

import com.beltforblind.route.model.RoutePoint
import com.beltforblind.route.model.RouteRecord
import com.beltforblind.route.recorder.RouteRecorder
import kotlin.random.Random

class MockRouteRecorder : RouteRecorder {
    private var recording = false
    private var pointCount = 0
    private var latestAccuracy: Float? = null
    private val random = Random(42)
    private val savedRoutes = mutableListOf<RouteRecord>()

    override fun startRecord() {
        recording = true
        pointCount = 0
        latestAccuracy = null
    }

    override fun stopRecord() {
        recording = false
    }

    override fun getPointCount(): Int {
        if (recording) {
            pointCount += 1
            latestAccuracy = random.nextFloat() * 5f + 2f
        }
        return pointCount
    }

    override fun getLatestAccuracy(): Float? = latestAccuracy

    override fun saveRoute(name: String): RouteRecord {
        require(name.isNotBlank()) { "Route name must not be blank." }

        val now = System.currentTimeMillis()
        val points = List(pointCount) { index ->
            RoutePoint(
                latitude = 31.2304 + index * 0.00001,
                longitude = 121.4737 + index * 0.00001,
                timestamp = now + index * 1000L,
                accuracy = latestAccuracy,
            )
        }
        val route = RouteRecord(
            id = "mock_$now",
            name = name,
            createdAt = now,
            points = points,
        )
        savedRoutes += route
        return route
    }

    override fun loadRoutes(): List<RouteRecord> = savedRoutes.toList()
}
