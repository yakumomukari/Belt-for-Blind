package com.beltforblind.route.location

import android.content.Context
import android.location.Location
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps2d.LocationSource
import com.beltforblind.route.model.RoutePoint

class AMapMapLocationSource(
    context: Context,
    private val onLocationError: (code: Int, message: String) -> Unit = { _, _ -> },
) : LocationSource {
    private val appContext = context.applicationContext
    private var locationClient: AMapLocationClient? = null
    private var mapLocationListener: LocationSource.OnLocationChangedListener? = null
    private var lastReportedErrorCode: Int? = null
    private var simulationListener: ((RoutePoint) -> Unit)? = null

    override fun activate(listener: LocationSource.OnLocationChangedListener) {
        mapLocationListener = listener
        if (locationClient != null || simulationListener != null) {
            return
        }

        if (LocationSimulationProvider.isEnabled) {
            val listenerForSimulation: (RoutePoint) -> Unit = { point ->
                mapLocationListener?.onLocationChanged(point.toAndroidLocation())
            }
            simulationListener = listenerForSimulation
            LocationSimulationProvider.addListener(listenerForSimulation)
            return
        }

        AMapLocationClient.updatePrivacyShow(appContext, true, true)
        AMapLocationClient.updatePrivacyAgree(appContext, true)

        val option = AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            interval = LOCATION_INTERVAL_MS
            isOnceLocation = false
            isNeedAddress = false
            isMockEnable = false
            isLocationCacheEnable = false
            setGpsFirst(true)
        }

        try {
            locationClient = AMapLocationClient(appContext).apply {
                setLocationOption(option)
                setLocationListener { location ->
                    if (location == null) {
                        return@setLocationListener
                    }

                    if (location.errorCode == 0) {
                        lastReportedErrorCode = null
                        mapLocationListener?.onLocationChanged(location)
                    } else if (lastReportedErrorCode != location.errorCode) {
                        lastReportedErrorCode = location.errorCode
                        onLocationError(location.errorCode, location.errorInfo.orEmpty())
                    }
                }
                startLocation()
            }
        } catch (_: SecurityException) {
            deactivate()
        }
    }

    override fun deactivate() {
        mapLocationListener = null
        lastReportedErrorCode = null
        simulationListener?.let(LocationSimulationProvider::removeListener)
        simulationListener = null
        locationClient?.run {
            stopLocation()
            onDestroy()
        }
        locationClient = null
    }

    private companion object {
        const val LOCATION_INTERVAL_MS = 3000L
    }
}

private fun RoutePoint.toAndroidLocation(): Location {
    return Location("debug-virtual-gps").apply {
        latitude = this@toAndroidLocation.latitude
        longitude = this@toAndroidLocation.longitude
        time = timestamp
        accuracy = this@toAndroidLocation.accuracy ?: 3f
    }
}
