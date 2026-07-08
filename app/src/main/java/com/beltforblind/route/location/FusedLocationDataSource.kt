package com.beltforblind.route.location

import com.beltforblind.route.model.RoutePoint

class FusedLocationDataSource : LocationDataSource {
    override fun start(onPoint: (RoutePoint) -> Unit) {
        TODO("Connect FusedLocationProviderClient in a later implementation step.")
    }

    override fun stop() {
        TODO("Remove FusedLocationProviderClient updates in a later implementation step.")
    }
}
