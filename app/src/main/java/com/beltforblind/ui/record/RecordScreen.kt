package com.beltforblind.ui.record

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amap.api.maps2d.CameraUpdateFactory
import com.amap.api.maps2d.MapView
import com.amap.api.maps2d.model.LatLng
import com.amap.api.maps2d.model.LatLngBounds
import com.amap.api.maps2d.model.MarkerOptions
import com.amap.api.maps2d.model.PolylineOptions
import com.beltforblind.route.model.RoutePoint
import com.beltforblind.route.model.RouteRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: RecordViewModel = viewModel(
        factory = RecordViewModel.factory(context.applicationContext),
    )
    val uiState by viewModel.uiState.collectAsState()
    val pointCount by viewModel.pointCount.collectAsState()
    val accuracy by viewModel.latestAccuracy.collectAsState()
    val latestReceivedAccuracy by viewModel.latestReceivedAccuracy.collectAsState()
    val latestPointAccepted by viewModel.latestPointAccepted.collectAsState()
    val discardedPointCount by viewModel.discardedPointCount.collectAsState()
    val warmupRemainingSeconds by viewModel.warmupRemainingSeconds.collectAsState()
    val recentPoints by viewModel.recentPoints.collectAsState()
    val savedRoutes by viewModel.savedRoutes.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var routeName by remember { mutableStateOf("") }
    var selectedRoute by remember { mutableStateOf<RouteRecord?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.startRecording()
        } else {
            viewModel.onPermissionDenied()
        }
    }
    val requestLocationPermission = {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }
    val startRecording = {
        if (context.hasLocationPermission()) {
            viewModel.startRecording()
        } else {
            requestLocationPermission()
        }
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            RecordingUiState.SaveSuccess -> snackbarHostState.showSnackbar("路线保存成功")
            RecordingUiState.SaveFailure -> snackbarHostState.showSnackbar("路线保存失败")
            RecordingUiState.PermissionDenied -> snackbarHostState.showSnackbar("定位权限未授权")
            else -> Unit
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val selected = selectedRoute
        if (selected != null) {
            RouteDetailScreen(
                route = selected,
                onBack = { selectedRoute = null },
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(state = uiState)

            if (uiState is RecordingUiState.PermissionDenied) {
                PermissionRequestCard(onRequestPermission = requestLocationPermission)
            }

            DataRow(label = "已记录点数", value = pointCount.toString())
            DataRow(label = "已丢弃点数", value = discardedPointCount.toString())
            DataRow(
                label = "GPS 预热",
                value = if (warmupRemainingSeconds > 0L) "剩余 ${warmupRemainingSeconds} 秒" else "已完成",
            )
            DataRow(label = "最近已保存精度", value = accuracy?.let { "%.1f 米".format(it) } ?: "--")
            DataRow(
                label = "最近原始精度",
                value = latestReceivedAccuracy?.let { "%.1f 米".format(it) } ?: "--",
            )
            DataRow(
                label = "精度状态",
                value = latestPointAccepted.formatAccuracyStatus(),
                boldValue = true,
            )

            if (recentPoints.isNotEmpty()) {
                RecentPointList(points = recentPoints)
            }

            OutlinedTextField(
                value = routeName,
                onValueChange = { routeName = it },
                label = { Text("路线名称") },
                enabled = uiState is RecordingUiState.Stopped || uiState is RecordingUiState.SaveFailure,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = startRecording,
                    enabled = uiState is RecordingUiState.Idle ||
                        uiState is RecordingUiState.Stopped ||
                        uiState is RecordingUiState.SaveSuccess ||
                        uiState is RecordingUiState.SaveFailure,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("开始记录")
                }

                Button(
                    onClick = { viewModel.stopRecording() },
                    enabled = uiState is RecordingUiState.Recording,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("停止记录")
                }
            }

            Button(
                onClick = { viewModel.saveRoute(routeName) },
                enabled = (uiState is RecordingUiState.Stopped || uiState is RecordingUiState.SaveFailure) &&
                    routeName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存路线")
            }

            TextButton(
                onClick = { viewModel.loadRoutes() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("查看已保存路线")
            }

            if (savedRoutes.isNotEmpty()) {
                SavedRouteList(
                    routes = savedRoutes,
                    onRouteClick = { selectedRoute = it },
                )
            }
        }
    }
}

@Composable
private fun StatusCard(state: RecordingUiState) {
    val (text, color) = when (state) {
        RecordingUiState.Idle -> "未开始" to MaterialTheme.colorScheme.outline
        RecordingUiState.Recording -> "记录中" to Color(0xFF2E7D32)
        is RecordingUiState.Stopped -> "已停止，等待保存" to MaterialTheme.colorScheme.primary
        RecordingUiState.SaveSuccess -> "保存成功" to Color(0xFF2E7D32)
        RecordingUiState.SaveFailure -> "保存失败" to MaterialTheme.colorScheme.error
        RecordingUiState.PermissionDenied -> "定位权限未授权" to Color(0xFFFFA000)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineMedium,
                color = color,
            )

            if (state is RecordingUiState.Recording) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "正在接收 GPS 定位点",
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                )
            }
        }
    }
}

private fun Context.hasLocationPermission(): Boolean {
    val fineGranted = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    return fineGranted || coarseGranted
}

@Composable
private fun PermissionRequestCard(onRequestPermission: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("需要定位权限后才能记录路线")
            Button(onClick = onRequestPermission) {
                Text("重新申请定位权限")
            }
        }
    }
}

@Composable
private fun DataRow(
    label: String,
    value: String,
    boldValue: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (boldValue) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun RecentPointList(points: List<RoutePoint>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("最近定位点", style = MaterialTheme.typography.titleMedium)
        points.forEachIndexed { index, point ->
            PointCard(
                title = "最近第 ${index + 1} 个点",
                point = point,
            )
        }
    }
}

@Composable
private fun SavedRouteList(
    routes: List<RouteRecord>,
    onRouteClick: (RouteRecord) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("已保存路线", style = MaterialTheme.typography.titleMedium)
        routes.forEach { route ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRouteClick(route) },
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(route.name, style = MaterialTheme.typography.titleSmall)
                    Text("${route.points.size} 个点，点击查看详情")
                }
            }
        }
    }
}

@Composable
private fun RouteDetailScreen(
    route: RouteRecord,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("返回")
        }

        Text(route.name, style = MaterialTheme.typography.headlineMedium)
        DataRow(label = "路线 ID", value = route.id)
        DataRow(label = "创建时间", value = route.createdAt.formatTimestamp())
        DataRow(label = "定位点数量", value = route.points.size.toString())

        MapRoutePreview(points = route.points)
        RoutePreview(points = route.points)

        Text("定位点详情", style = MaterialTheme.typography.titleMedium)
        if (route.points.isEmpty()) {
            Text("这条路线没有有效定位点")
        } else {
            route.points.take(MAX_DETAIL_POINTS).forEachIndexed { index, point ->
                PointCard(
                    title = "第 ${index + 1} 个点",
                    point = point,
                )
            }
            if (route.points.size > MAX_DETAIL_POINTS) {
                Text("仅显示前 $MAX_DETAIL_POINTS 个点，共 ${route.points.size} 个点")
            }
        }
    }
}

@Composable
private fun MapRoutePreview(points: List<RoutePoint>) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDestroy()
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("地图预览", style = MaterialTheme.typography.titleMedium)
            AndroidView(
                factory = { mapView },
                update = { view ->
                    view.renderRoute(points)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp),
            )
            Text(
                text = "高德 2D 地图预览：蓝色为起点，红色为终点，绿色为路径点。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RoutePreview(points: List<RoutePoint>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("路径点预览", style = MaterialTheme.typography.titleMedium)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            ) {
                if (points.isEmpty()) {
                    return@Canvas
                }

                val minLat = points.minOf { it.latitude }
                val maxLat = points.maxOf { it.latitude }
                val minLon = points.minOf { it.longitude }
                val maxLon = points.maxOf { it.longitude }
                val latRange = (maxLat - minLat).takeIf { it != 0.0 } ?: 1.0
                val lonRange = (maxLon - minLon).takeIf { it != 0.0 } ?: 1.0
                val paddingPx = 24.dp.toPx()

                fun mapPoint(point: RoutePoint): Offset {
                    val xRatio = ((point.longitude - minLon) / lonRange).toFloat()
                    val yRatio = ((point.latitude - minLat) / latRange).toFloat()
                    val x = paddingPx + xRatio * (size.width - paddingPx * 2)
                    val y = size.height - paddingPx - yRatio * (size.height - paddingPx * 2)
                    return Offset(x, y)
                }

                val offsets = points.map(::mapPoint)
                for (index in 0 until offsets.lastIndex) {
                    drawLine(
                        color = Color(0xFF2E7D32),
                        start = offsets[index],
                        end = offsets[index + 1],
                        strokeWidth = 5f,
                        cap = StrokeCap.Round,
                    )
                }

                offsets.forEachIndexed { index, offset ->
                    val color = when (index) {
                        0 -> Color(0xFF1565C0)
                        offsets.lastIndex -> Color(0xFFC62828)
                        else -> Color(0xFF2E7D32)
                    }
                    drawCircle(color = color, radius = 7f, center = offset)
                }
            }
        }
    }
}

@Composable
private fun PointCard(
    title: String,
    point: RoutePoint,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
            Text("纬度：%.6f".format(point.latitude))
            Text("经度：%.6f".format(point.longitude))
            Text("时间：${point.timestamp.formatTimestamp()}")
            Text("精度：${point.accuracy?.let { "%.1f 米".format(it) } ?: "--"}")
        }
    }
}

private fun Long.formatTimestamp(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(this))
}

private fun Boolean?.formatAccuracyStatus(): String {
    return when (this) {
        true -> "合格，已保存"
        false -> "不合格，已丢弃"
        null -> "--"
    }
}

private fun MapView.renderRoute(points: List<RoutePoint>) {
    val aMap = map
    aMap.clear()

    val latLngs = points.map { LatLng(it.latitude, it.longitude) }
    if (latLngs.isEmpty()) {
        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(31.2304, 121.4737), 15f))
        return
    }

    if (latLngs.size > 1) {
        aMap.addPolyline(
            PolylineOptions()
                .addAll(latLngs)
                .width(14f)
                .color(POLYLINE_COLOR),
        )
    }

    latLngs.forEachIndexed { index, latLng ->
        val isStart = index == 0
        val isEnd = index == latLngs.lastIndex
        val color = when {
            isStart -> START_HUE
            isEnd -> END_HUE
            else -> ROUTE_HUE
        }

        if (isStart || isEnd || latLngs.size <= MAX_VISIBLE_MARKERS) {
            aMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("点 ${index + 1}")
                    .snippet(
                        "lat=%.6f, lng=%.6f".format(
                            Locale.US,
                            latLng.latitude,
                            latLng.longitude,
                        ),
                    )
                    .anchor(0.5f, 0.5f),
            )?.apply {
                setIcon(com.amap.api.maps2d.model.BitmapDescriptorFactory.defaultMarker(color))
            }
        }
    }

    if (latLngs.size == 1) {
        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLngs.first(), 20f))
        return
    }

    val bounds = LatLngBounds.Builder().also { builder ->
        latLngs.forEach(builder::include)
    }.build()
    aMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 24))

    val latSpan = points.maxOf { it.latitude } - points.minOf { it.latitude }
    val lngSpan = points.maxOf { it.longitude } - points.minOf { it.longitude }
    val targetZoom = if (latSpan < TINY_ROUTE_DEGREES && lngSpan < TINY_ROUTE_DEGREES) {
        20f
    } else if (latSpan < SMALL_ROUTE_DEGREES && lngSpan < SMALL_ROUTE_DEGREES) {
        19f
    } else {
        18f
    }
    if (aMap.cameraPosition.zoom < targetZoom) {
        aMap.moveCamera(CameraUpdateFactory.zoomTo(targetZoom))
    }
}

private const val START_HUE = 210f
private const val END_HUE = 0f
private const val ROUTE_HUE = 120f
private val POLYLINE_COLOR = android.graphics.Color.rgb(46, 125, 50)
private const val TINY_ROUTE_DEGREES = 0.001
private const val SMALL_ROUTE_DEGREES = 0.003
private const val MAX_VISIBLE_MARKERS = 80
private const val MAX_DETAIL_POINTS = 20
