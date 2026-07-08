package com.beltforblind.route.location

import com.beltforblind.route.model.RoutePoint

interface LocationDataSource {
    fun start(onPoint: (RoutePoint) -> Unit)

    fun stop()
}
