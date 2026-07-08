package com.beltforblind.route.location

interface LocationPermissionGateway {
    fun hasLocationPermission(): Boolean

    fun requestLocationPermission()
}
