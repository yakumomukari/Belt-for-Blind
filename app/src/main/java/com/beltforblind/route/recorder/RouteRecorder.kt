package com.beltforblind.route.recorder

import com.beltforblind.route.model.RouteRecord

interface RouteRecorder {
    fun startRecord()

    fun stopRecord()

    fun getPointCount(): Int

    fun getLatestAccuracy(): Float?

    fun saveRoute(name: String): RouteRecord

    fun loadRoutes(): List<RouteRecord>
}
