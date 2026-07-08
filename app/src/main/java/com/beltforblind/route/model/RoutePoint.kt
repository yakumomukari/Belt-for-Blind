package com.beltforblind.route.model

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val accuracy: Float?,
)
