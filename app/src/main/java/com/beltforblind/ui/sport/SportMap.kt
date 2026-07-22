package com.beltforblind.ui.sport

import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.amap.api.maps2d.CameraUpdateFactory
import com.amap.api.maps2d.MapView
import com.amap.api.maps2d.model.BitmapDescriptorFactory
import com.amap.api.maps2d.model.LatLng
import com.amap.api.maps2d.model.Marker
import com.amap.api.maps2d.model.MarkerOptions
import com.amap.api.maps2d.model.MyLocationStyle
import com.amap.api.maps2d.model.Polyline
import com.amap.api.maps2d.model.PolylineOptions
import com.beltforblind.route.location.AMapMapLocationSource
import com.beltforblind.route.model.RoutePoint
import com.beltforblind.route.model.RouteRecord
import com.beltforblind.route.tangent.RouteTangent

@Composable
internal fun SportMap(
    route: RouteRecord?,
    routeTangent: RouteTangent?,
    currentLocation: RoutePoint?,
    locationPermissionGranted: Boolean,
    followUser: Boolean,
    recenterRequestId: Long,
    onLocationUpdated: (RoutePoint) -> Unit,
    onLocationError: (code: Int, message: String) -> Unit,
    onUserMapInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentLocationCallback by rememberUpdatedState(onLocationUpdated)
    val locationErrorCallback by rememberUpdatedState(onLocationError)
    val mapInteractionCallback by rememberUpdatedState(onUserMapInteraction)
    val locationSource = remember {
        AMapMapLocationSource(
            context = context.applicationContext,
            onLocationError = { code, message -> locationErrorCallback(code, message) },
            onLocation = { point -> currentLocationCallback(point) },
        )
    }
    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            map.uiSettings.isZoomControlsEnabled = false
            map.uiSettings.isZoomGesturesEnabled = false
            map.uiSettings.isMyLocationButtonEnabled = false
            map.moveCamera(CameraUpdateFactory.zoomTo(FIXED_MAP_ZOOM))
            map.setLocationSource(locationSource)
            map.setOnMapTouchListener { event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    mapInteractionCallback()
                }
            }
        }
    }
    val controller = remember(mapView) { SportMapController(mapView) }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.map.isMyLocationEnabled = false
            locationSource.deactivate()
            mapView.onPause()
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        update = { view ->
            if (view.map.isMyLocationEnabled != locationPermissionGranted) {
                view.map.isMyLocationEnabled = locationPermissionGranted
            }
            controller.updateRoute(
                route = route,
                focusOnRouteStart = currentLocation == null,
            )
            controller.updateCompletedRoute(route, routeTangent)
            controller.updateFollowMode(followUser)
            controller.centerOnInitialLocation(currentLocation)
            controller.handleRecenter(recenterRequestId, currentLocation)
        },
        modifier = modifier,
    )
}

private class SportMapController(
    private val mapView: MapView,
) {
    private var renderedRouteSignature: String? = null
    private var routePolyline: Polyline? = null
    private var completedPolyline: Polyline? = null
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null
    private var followMode: Boolean? = null
    private var initialLocationCentered = false
    private var handledRecenterRequestId = 0L
    private var renderedProgressSignature: String? = null

    fun updateRoute(
        route: RouteRecord?,
        focusOnRouteStart: Boolean,
    ) {
        val signature = route?.let { "${it.id}:${it.points.size}:${it.points.lastOrNull()?.timestamp}" }
        if (signature == renderedRouteSignature) return

        routePolyline?.remove()
        completedPolyline?.remove()
        startMarker?.remove()
        endMarker?.remove()
        routePolyline = null
        completedPolyline = null
        startMarker = null
        endMarker = null
        renderedRouteSignature = signature
        renderedProgressSignature = null

        val points = route?.points.orEmpty().map { LatLng(it.latitude, it.longitude) }
        if (points.isEmpty()) return

        if (points.size > 1) {
            routePolyline = mapView.map.addPolyline(
                PolylineOptions()
                    .addAll(points)
                    .width(10f)
                    .color(REMAINING_ROUTE_COLOR),
            )
        }
        startMarker = mapView.map.addMarker(
            MarkerOptions()
                .position(points.first())
                .title("路线起点")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)),
        )
        endMarker = mapView.map.addMarker(
            MarkerOptions()
                .position(points.last())
                .title("路线终点")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)),
        )
        if (focusOnRouteStart) {
            mapView.post {
                mapView.map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(points.first(), FIXED_MAP_ZOOM),
                )
            }
        }
    }

    fun updateCompletedRoute(route: RouteRecord?, tangent: RouteTangent?) {
        val signature = if (route == null || tangent == null) null else {
            "${route.id}:${tangent.segmentStartIndex}:${tangent.projectionRatio}"
        }
        if (signature == renderedProgressSignature) return
        renderedProgressSignature = signature
        completedPolyline?.remove()
        completedPolyline = null

        if (route == null || tangent == null) return
        val routePoints = route.points.map { LatLng(it.latitude, it.longitude) }
        if (tangent.segmentStartIndex !in routePoints.indices || tangent.segmentEndIndex !in routePoints.indices) {
            return
        }
        val completedPoints = routePoints.take(tangent.segmentStartIndex + 1).toMutableList()
        if (tangent.projectionRatio > 0.0) {
            completedPoints += interpolate(
                routePoints[tangent.segmentStartIndex],
                routePoints[tangent.segmentEndIndex],
                tangent.projectionRatio,
            )
        }
        if (completedPoints.size > 1) {
            completedPolyline = mapView.map.addPolyline(
                PolylineOptions()
                    .addAll(completedPoints)
                    .width(16f)
                    .color(COMPLETED_ROUTE_COLOR),
            )
        }
    }

    private fun interpolate(start: LatLng, end: LatLng, ratio: Double): LatLng {
        return LatLng(
            start.latitude + (end.latitude - start.latitude) * ratio,
            start.longitude + (end.longitude - start.longitude) * ratio,
        )
    }

    fun updateFollowMode(followUser: Boolean) {
        if (followMode == followUser) return
        followMode = followUser
        mapView.map.setMyLocationStyle(
            MyLocationStyle()
                .myLocationType(
                    if (followUser) {
                        MyLocationStyle.LOCATION_TYPE_FOLLOW
                    } else {
                        MyLocationStyle.LOCATION_TYPE_SHOW
                    },
                )
                .interval(LOCATION_INTERVAL_MS)
                .strokeColor(LOCATION_STROKE_COLOR)
                .radiusFillColor(LOCATION_RADIUS_COLOR)
                .strokeWidth(2f),
        )
    }

    fun centerOnInitialLocation(location: RoutePoint?) {
        if (initialLocationCentered || location == null) return
        initialLocationCentered = true
        mapView.map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude),
                FIXED_MAP_ZOOM,
            ),
        )
    }

    fun handleRecenter(requestId: Long, location: RoutePoint?) {
        if (requestId == handledRecenterRequestId || location == null) return
        handledRecenterRequestId = requestId
        mapView.map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude),
                FIXED_MAP_ZOOM,
            ),
        )
    }

    private companion object {
        const val LOCATION_INTERVAL_MS = 3_000L
        val REMAINING_ROUTE_COLOR = android.graphics.Color.argb(170, 110, 117, 108)
        val COMPLETED_ROUTE_COLOR = android.graphics.Color.rgb(35, 122, 56)
        val LOCATION_STROKE_COLOR = android.graphics.Color.rgb(21, 101, 192)
        val LOCATION_RADIUS_COLOR = android.graphics.Color.argb(40, 21, 101, 192)
    }
}

private const val FIXED_MAP_ZOOM = 20f
