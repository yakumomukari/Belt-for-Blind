package com.beltforblind.route.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.beltforblind.route.model.RoutePoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class FusedLocationDataSource(
    context: Context,
) : LocationDataSource {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context.applicationContext)

    private var locationCallback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    override fun start(onPoint: (RoutePoint) -> Unit) {
        stop()

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_LOCATION_INTERVAL_MS)
            .setWaitForAccurateLocation(true)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    onPoint(
                        RoutePoint(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            timestamp = if (location.time > 0L) location.time else System.currentTimeMillis(),
                            accuracy = if (location.hasAccuracy()) location.accuracy else null,
                        ),
                    )
                }
            }
        }

        locationCallback = callback
        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    override fun stop() {
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
        }
        locationCallback = null
    }

    private companion object {
        const val LOCATION_INTERVAL_MS = 1000L
        const val FASTEST_LOCATION_INTERVAL_MS = 500L
    }
}
