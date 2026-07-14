package com.beltforblind.route.recorder

import com.beltforblind.route.location.LocationDataSource
import com.beltforblind.route.model.RoutePoint
import com.beltforblind.route.model.RouteRecord
import com.beltforblind.route.storage.RouteStore

class RouteRecordingManager(
    private val locationDataSource: LocationDataSource,
    private val routeStore: RouteStore,
) : RouteRecorder {
    private val rawPoints = mutableListOf<RoutePoint>()
    private var isRecording = false
    private var latestAccuracy: Float? = null
    private var latestReceivedAccuracy: Float? = null
    private var latestPointAccepted: Boolean? = null
    private var discardedPointCount = 0
    private var recordingStartedAt = 0L

    override fun startRecord() {
        if (isRecording) {
            stopRecord()
        }

        rawPoints.clear()
        latestAccuracy = null
        latestReceivedAccuracy = null
        latestPointAccepted = null
        discardedPointCount = 0
        recordingStartedAt = System.currentTimeMillis()
        isRecording = true

        try {
            locationDataSource.start { point ->
                if (!isRecording) {
                    return@start
                }

                val accuracy = point.accuracy
                latestReceivedAccuracy = accuracy
                if (isInWarmup()) {
                    latestPointAccepted = false
                    discardedPointCount += 1
                    return@start
                }

                if (accuracy == null || accuracy > MAX_ACCEPTED_ACCURACY_METERS) {
                    latestPointAccepted = false
                    discardedPointCount += 1
                    return@start
                }

                rawPoints += point
                latestAccuracy = accuracy
                latestPointAccepted = true
            }
        } catch (error: SecurityException) {
            isRecording = false
            throw error
        }
    }

    override fun stopRecord() {
        if (!isRecording) {
            return
        }

        isRecording = false
        locationDataSource.stop()
    }

    override fun getPointCount(): Int {
        return rawPoints.size
    }

    override fun getLatestAccuracy(): Float? {
        return latestAccuracy
    }

    override fun getLatestReceivedAccuracy(): Float? {
        return latestReceivedAccuracy
    }

    override fun isLatestPointAccepted(): Boolean? {
        return latestPointAccepted
    }

    override fun getDiscardedPointCount(): Int {
        return discardedPointCount
    }

    override fun getWarmupRemainingSeconds(): Long {
        if (!isRecording || recordingStartedAt == 0L) {
            return 0L
        }

        val elapsedMs = System.currentTimeMillis() - recordingStartedAt
        val remainingMs = (WARMUP_DURATION_MS - elapsedMs).coerceAtLeast(0L)
        return (remainingMs + 999L) / 1000L
    }

    override fun getCurrentPoints(): List<RoutePoint> {
        return rawPoints.toList()
    }

    override fun saveRoute(name: String): RouteRecord {
        require(name.isNotBlank()) { "Route name must not be blank." }

        val createdAt = System.currentTimeMillis()
        val route = RouteRecord(
            id = "route_$createdAt",
            name = name,
            createdAt = createdAt,
            points = rawPoints.toList(),
        )
        return routeStore.save(route)
    }

    override fun loadRoutes(): List<RouteRecord> {
        return routeStore.loadAll()
    }

    override fun deleteRoute(routeId: String): Boolean {
        return routeStore.delete(routeId)
    }

    private companion object {
        const val MAX_ACCEPTED_ACCURACY_METERS = 8f
        const val WARMUP_DURATION_MS = 15_000L
    }

    private fun isInWarmup(): Boolean {
        return recordingStartedAt > 0L &&
            System.currentTimeMillis() - recordingStartedAt < WARMUP_DURATION_MS
    }
}
