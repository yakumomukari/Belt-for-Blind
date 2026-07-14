package com.beltforblind.route.storage

import com.beltforblind.route.model.RouteRecord

interface RouteStore {
    fun save(route: RouteRecord): RouteRecord

    fun loadAll(): List<RouteRecord>

    fun delete(routeId: String): Boolean
}
