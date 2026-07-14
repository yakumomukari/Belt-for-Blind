package com.beltforblind.route.recorder

import com.beltforblind.route.model.RouteRecord
import com.beltforblind.route.model.RoutePoint

interface RouteRecorder {
    fun startRecord()

    fun stopRecord()

    fun getPointCount(): Int

    fun getLatestAccuracy(): Float?

    fun getLatestReceivedAccuracy(): Float?

    fun isLatestPointAccepted(): Boolean?

    fun getDiscardedPointCount(): Int

    fun getWarmupRemainingSeconds(): Long

    fun getCurrentPoints(): List<RoutePoint>

    fun saveRoute(name: String): RouteRecord

    fun loadRoutes(): List<RouteRecord>

    fun deleteRoute(routeId: String): Boolean
}
