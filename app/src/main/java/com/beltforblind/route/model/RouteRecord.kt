package com.beltforblind.route.model

data class RouteRecord(
    val id: String,
    val name: String,
    val createdAt: Long,
    val points: List<RoutePoint>,
)
