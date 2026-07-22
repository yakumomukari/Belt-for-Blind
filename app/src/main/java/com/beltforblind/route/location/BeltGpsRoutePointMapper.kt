package com.beltforblind.route.location

import com.beltforblind.motor.BeltGpsSample
import com.beltforblind.route.model.RoutePoint

object BeltGpsRoutePointMapper {
    fun toRoutePoint(sample: BeltGpsSample): RoutePoint? {
        if (!sample.isFixValid) return null
        val coordinate = Wgs84ToGcj02Converter.convert(
            latitude = sample.latitudeWgs84,
            longitude = sample.longitudeWgs84,
        )
        return RoutePoint(
            latitude = coordinate.latitude,
            longitude = coordinate.longitude,
            timestamp = sample.receivedAtMillis,
            accuracy = sample.horizontalAccuracyMeters,
        )
    }
}
