package com.beltforblind.route.location

import android.content.Context
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.beltforblind.route.model.RoutePoint

class AMapLocationDataSource(
    context: Context,
) : LocationDataSource {
    private val appContext = context.applicationContext
    private var locationClient: AMapLocationClient? = null

    override fun start(onPoint: (RoutePoint) -> Unit) {
        stop()

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

        locationClient = AMapLocationClient(appContext).apply {
            setLocationOption(option)
            setLocationListener { location ->
                if (location == null || location.errorCode != 0) {
                    return@setLocationListener
                }

                onPoint(
                    RoutePoint(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        timestamp = if (location.time > 0L) location.time else System.currentTimeMillis(),
                        accuracy = location.accuracy.takeIf { it > 0f },
                    ),
                )
            }
            startLocation()
        }
    }

    override fun stop() {
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
