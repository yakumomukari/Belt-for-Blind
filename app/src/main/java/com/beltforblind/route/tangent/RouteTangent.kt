package com.beltforblind.route.tangent

data class RouteTangent(
    val segmentStartIndex: Int,
    val segmentEndIndex: Int,
    val projectionRatio: Double,
    val tangentBearingDegrees: Double,
    val distanceToRouteMeters: Double,
    val alongRouteDistanceMeters: Double,
    val totalRouteDistanceMeters: Double,
) {
    val progress: Double
        get() = if (totalRouteDistanceMeters > 0.0) {
            (alongRouteDistanceMeters / totalRouteDistanceMeters).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
}
