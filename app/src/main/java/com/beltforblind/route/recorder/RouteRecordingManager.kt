package com.beltforblind.route.recorder

import com.beltforblind.route.location.LocationDataSource
import com.beltforblind.route.model.RouteRecord
import com.beltforblind.route.storage.RouteStore

class RouteRecordingManager(
    private val locationDataSource: LocationDataSource,
    private val routeStore: RouteStore,
) : RouteRecorder {
    override fun startRecord() {
        TODO("Start location listening in a later implementation step.")
    }

    override fun stopRecord() {
        TODO("Stop location listening in a later implementation step.")
    }

    override fun getPointCount(): Int {
        TODO("Return current raw point count in a later implementation step.")
    }

    override fun getLatestAccuracy(): Float? {
        TODO("Return latest accepted accuracy in a later implementation step.")
    }

    override fun saveRoute(name: String): RouteRecord {
        TODO("Build and persist a route object in a later implementation step.")
    }

    override fun loadRoutes(): List<RouteRecord> {
        TODO("Load saved routes from storage in a later implementation step.")
    }
}
