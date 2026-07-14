package com.beltforblind.route.location

import android.content.Context
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps2d.LocationSource

class AMapMapLocationSource(
    context: Context,
    private val onLocationError: (code: Int, message: String) -> Unit = { _, _ -> },
) : LocationSource {
    private val appContext = context.applicationContext
    private var locationClient: AMapLocationClient? = null
    private var mapLocationListener: LocationSource.OnLocationChangedListener? = null
    private var lastReportedErrorCode: Int? = null

    override fun activate(listener: LocationSource.OnLocationChangedListener) {
        mapLocationListener = listener
        if (locationClient != null) {
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
