package com.beltforblind.ui.record

import com.beltforblind.route.model.RoutePoint
import com.beltforblind.route.model.RouteRecord
import com.beltforblind.route.recorder.RouteRecorder
import kotlin.random.Random

class MockRouteRecorder : RouteRecorder {
    private var recording = false
    private var latestAccuracy: Float? = null
    private var latestReceivedAccuracy: Float? = null
    private var latestPointAccepted: Boolean? = null
    private var discardedPointCount = 0
    private var recordingStartedAt = 0L
    private val random = Random(42)
    private val currentPoints = mutableListOf<RoutePoint>()
    private val savedRoutes = mutableListOf<RouteRecord>()

    override fun startRecord() {
        recording = true
        currentPoints.clear()
        latestAccuracy = null
        latestReceivedAccuracy = null
        latestPointAccepted = null
        discardedPointCount = 0
        recordingStartedAt = System.currentTimeMillis()
    }

    override fun stopRecord() {
        recording = false
    }

    override fun pauseRecord() {
        recording = false
    }

    override fun resumeRecord() {
        if (recordingStartedAt != 0L) recording = true
    }

    override fun getPointCount(): Int {
        if (recording) {
            latestReceivedAccuracy = random.nextFloat() * 25f + 2f
            val warmingUp = getWarmupRemainingSeconds() > 0L
            latestPointAccepted = !warmingUp && latestReceivedAccuracy?.let { it <= 8f } == true
            if (latestPointAccepted != true) {
                discardedPointCount += 1
                return currentPoints.size
            }

            latestAccuracy = latestReceivedAccuracy
            val index = currentPoints.size
            currentPoints += RoutePoint(
                latitude = 31.2304 + index * 0.00001,
                longitude = 121.4737 + index * 0.00001,
                timestamp = System.currentTimeMillis(),
                accuracy = latestAccuracy,
            )
        }
        return currentPoints.size
    }

    override fun getLatestAccuracy(): Float? = latestAccuracy

    override fun getLatestReceivedAccuracy(): Float? = latestReceivedAccuracy

    override fun isLatestPointAccepted(): Boolean? = latestPointAccepted

    override fun getDiscardedPointCount(): Int = discardedPointCount

    override fun getWarmupRemainingSeconds(): Long {
        if (!recording || recordingStartedAt == 0L) {
            return 0L
        }

        val remainingMs = (15_000L - (System.currentTimeMillis() - recordingStartedAt)).coerceAtLeast(0L)
        return (remainingMs + 999L) / 1000L
    }

    override fun getCurrentPoints(): List<RoutePoint> = currentPoints.toList()

    override fun saveRoute(name: String): RouteRecord {
        require(name.isNotBlank()) { "Route name must not be blank." }

        val now = System.currentTimeMillis()
        val route = RouteRecord(
            id = "mock_$now",
            name = name,
            createdAt = now,
            points = currentPoints.toList(),
        )
        savedRoutes += route
        return route
    }

    override fun loadRoutes(): List<RouteRecord> = savedRoutes.toList()

    override fun deleteRoute(routeId: String): Boolean {
        return savedRoutes.removeAll { it.id == routeId }
    }
}
