package com.beltforblind.route.storage

import com.beltforblind.route.model.RouteRecord

class JsonRouteStore : RouteStore {
    override fun save(route: RouteRecord): RouteRecord {
        TODO("Persist route JSON under filesDir/routes in a later implementation step.")
    }

    override fun loadAll(): List<RouteRecord> {
        TODO("Read route JSON files from filesDir/routes in a later implementation step.")
    }
}
