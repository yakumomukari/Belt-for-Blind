package com.beltforblind.ui.saved

import android.graphics.Bitmap
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.amap.api.maps2d.AMap
import com.amap.api.maps2d.CameraUpdateFactory
import com.amap.api.maps2d.MapView
import com.amap.api.maps2d.model.BitmapDescriptorFactory
import com.amap.api.maps2d.model.LatLng
import com.amap.api.maps2d.model.LatLngBounds
import com.amap.api.maps2d.model.MarkerOptions
import com.amap.api.maps2d.model.PolylineOptions
import com.beltforblind.route.model.RouteRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@Composable
internal fun RouteThumbnailRendererHost(
    routes: List<RouteRecord>,
    cache: RouteThumbnailCache,
    onThumbnailGenerated: () -> Unit,
) {
    val context = LocalContext.current
    var mapLoaded by remember { mutableStateOf(false) }
    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            map.uiSettings.apply {
                isScaleControlsEnabled = false
                isZoomControlsEnabled = false
                isCompassEnabled = false
                isMyLocationButtonEnabled = false
                isScrollGesturesEnabled = false
                isZoomGesturesEnabled = false
            }
            map.setOnMapLoadedListener {
                mapLoaded = true
            }
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier
            .size(THUMBNAIL_RENDER_SIZE_DP.dp)
            .offset(x = OFFSCREEN_OFFSET_DP.dp),
    )

    LaunchedEffect(routes, mapLoaded) {
        if (!mapLoaded) return@LaunchedEffect

        routes.forEach { route ->
            if (route.points.isEmpty() || cache.contains(route)) return@forEach
            if (!mapView.renderThumbnailRoute(route)) return@forEach

            delay(MAP_SETTLE_DELAY_MS)
            val bitmap = mapView.map.captureScreen() ?: return@forEach
            val saved = withContext(Dispatchers.IO) {
                cache.save(route, bitmap)
            }
            bitmap.recycle()
            if (saved) onThumbnailGenerated()
        }
    }
}

private fun MapView.renderThumbnailRoute(route: RouteRecord): Boolean {
    val routePoints = route.points.map { point ->
        LatLng(point.latitude, point.longitude)
    }
    if (routePoints.isEmpty()) return false

    map.clear()
    if (routePoints.size > 1) {
        map.addPolyline(
            PolylineOptions()
                .addAll(routePoints)
                .width(10f)
                .color(THUMBNAIL_ROUTE_COLOR)
                .zIndex(2f),
        )
    }

    map.addMarker(
        MarkerOptions()
            .position(routePoints.first())
            .icon(BitmapDescriptorFactory.defaultMarker(START_MARKER_HUE))
            .anchor(0.5f, 0.5f),
    )
    if (routePoints.size > 1) {
        map.addMarker(
            MarkerOptions()
                .position(routePoints.last())
                .icon(BitmapDescriptorFactory.defaultMarker(END_MARKER_HUE))
                .anchor(0.5f, 0.5f),
        )
    }

    val latitudeSpan = route.points.maxOf { it.latitude } - route.points.minOf { it.latitude }
    val longitudeSpan = route.points.maxOf { it.longitude } - route.points.minOf { it.longitude }
    if (routePoints.size == 1 || (latitudeSpan < MIN_ROUTE_SPAN && longitudeSpan < MIN_ROUTE_SPAN)) {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(routePoints.first(), SINGLE_POINT_ZOOM))
    } else {
        val bounds = LatLngBounds.Builder().also { builder ->
            routePoints.forEach(builder::include)
        }.build()
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, BOUNDS_PADDING_PX))
    }
    return true
}

private suspend fun AMap.captureScreen(): Bitmap? {
    return suspendCancellableCoroutine { continuation ->
        getMapScreenShot { bitmap ->
            if (continuation.isActive) {
                continuation.resume(bitmap)
            }
        }
    }
}

private const val THUMBNAIL_RENDER_SIZE_DP = 160
private const val OFFSCREEN_OFFSET_DP = -1000
private const val MAP_SETTLE_DELAY_MS = 900L
private const val BOUNDS_PADDING_PX = 18
private const val SINGLE_POINT_ZOOM = 18f
private const val MIN_ROUTE_SPAN = 0.00001
private const val START_MARKER_HUE = 270f
private const val END_MARKER_HUE = 210f
private val THUMBNAIL_ROUTE_COLOR = android.graphics.Color.rgb(113, 68, 199)
