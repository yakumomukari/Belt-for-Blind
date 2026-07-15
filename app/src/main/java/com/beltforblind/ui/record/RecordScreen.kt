package com.beltforblind.ui.record

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amap.api.maps2d.CameraUpdateFactory
import com.amap.api.maps2d.MapView
import com.amap.api.maps2d.model.LatLng
import com.amap.api.maps2d.model.LatLngBounds
import com.amap.api.maps2d.model.MarkerOptions
import com.amap.api.maps2d.model.MyLocationStyle
import com.amap.api.maps2d.model.PolylineOptions
import com.beltforblind.route.location.AMapMapLocationSource
import com.beltforblind.route.model.RoutePoint
import com.beltforblind.route.model.RouteRecord
import com.beltforblind.route.tangent.RouteTangent
import com.beltforblind.route.tangent.RouteTangentCalculator
import com.beltforblind.ui.theme.BeltColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RecordScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: RecordViewModel = viewModel(
        factory = RecordViewModel.factory(context.applicationContext),
    )
    val uiState by viewModel.uiState.collectAsState()
    val pointCount by viewModel.pointCount.collectAsState()
    val recentPoints by viewModel.recentPoints.collectAsState()
    val latestReceivedAccuracy by viewModel.latestReceivedAccuracy.collectAsState()
    val latestPointAccepted by viewModel.latestPointAccepted.collectAsState()
    val warmupRemainingSeconds by viewModel.warmupRemainingSeconds.collectAsState()
    val discardedPointCount by viewModel.discardedPointCount.collectAsState()
    val elapsedTimeSeconds by viewModel.elapsedTimeSeconds.collectAsState()
    val distanceMeters = remember(recentPoints) { recentPoints.totalRecordedDistanceMeters() }
    val snackbarHostState = remember { SnackbarHostState() }
    var routeName by remember { mutableStateOf("") }
    var showStopConfirmDialog by remember { mutableStateOf(false) }
    var showSavePanel by remember { mutableStateOf(false) }
    var locationPermissionGranted by remember { mutableStateOf(context.hasLocationPermission()) }
    var startRecordingAfterPermission by remember { mutableStateOf(false) }
    var mapLocationError by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        locationPermissionGranted = granted
        if (granted) {
            if (startRecordingAfterPermission) {
                viewModel.startRecording()
            } else {
                viewModel.resetToIdle()
            }
        } else {
            viewModel.onPermissionDenied()
        }
        startRecordingAfterPermission = false
    }
    val requestLocationPermission: (Boolean) -> Unit = { startRecordingAfterGrant ->
        startRecordingAfterPermission = startRecordingAfterGrant
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }
    val startRecording = {
        routeName = ""
        showStopConfirmDialog = false
        showSavePanel = false
        if (context.hasLocationPermission()) {
            locationPermissionGranted = true
            viewModel.startRecording()
        } else {
            requestLocationPermission(true)
        }
    }

    LaunchedEffect(Unit) {
        if (!locationPermissionGranted) {
            requestLocationPermission(false)
        }
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            RecordingUiState.SaveSuccess -> {
                snackbarHostState.showSnackbar("路线保存成功")
                routeName = ""
                showSavePanel = false
                viewModel.resetToIdle()
            }
            RecordingUiState.SaveFailure -> {
                showSavePanel = true
                snackbarHostState.showSnackbar("路线保存失败")
            }
            RecordingUiState.PermissionDenied -> snackbarHostState.showSnackbar("定位权限未授权")
            else -> Unit
        }
    }

    LaunchedEffect(mapLocationError) {
        mapLocationError?.let { message ->
            snackbarHostState.showSnackbar(message)
            mapLocationError = null
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (
                (uiState is RecordingUiState.Recording || uiState is RecordingUiState.Paused) &&
                !showSavePanel &&
                !showStopConfirmDialog
            ) {
                ActiveRecordingContent(
                    points = recentPoints,
                    pointCount = pointCount,
                    distanceMeters = distanceMeters,
                    elapsedTimeSeconds = elapsedTimeSeconds,
                    accuracy = latestReceivedAccuracy,
                    latestPointAccepted = latestPointAccepted,
                    discardedPointCount = discardedPointCount,
                    warmupRemainingSeconds = warmupRemainingSeconds,
                    isPaused = uiState is RecordingUiState.Paused,
                    locationPermissionGranted = locationPermissionGranted,
                    onLocationError = { code, message ->
                        mapLocationError = "定位失败（$code）：$message"
                    },
                    onPause = viewModel::pauseRecording,
                    onResume = viewModel::resumeRecording,
                    onFinish = {
                        viewModel.stopRecording()
                        showStopConfirmDialog = true
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                RecordingMapBackground(
                    points = recentPoints,
                    locationPermissionGranted = locationPermissionGranted,
                    onLocationError = { code, message ->
                        mapLocationError = "定位失败（$code）：$message"
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                if (uiState is RecordingUiState.PermissionDenied) {
                    PermissionRequestCard(
                        onRequestPermission = { requestLocationPermission(false) },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                }

                if (showSavePanel) {
                    SaveRoutePanel(
                        pointCount = pointCount,
                        routeName = routeName,
                        onRouteNameChange = { routeName = it },
                        onSave = { viewModel.saveRoute(routeName) },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                } else if (!showStopConfirmDialog) {
                    MainRecordButton(
                        text = "GO",
                        enabled = uiState !is RecordingUiState.PermissionDenied,
                        active = false,
                        onClick = startRecording,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp),
                    )
                }
            }

            if (showStopConfirmDialog) {
                StopConfirmDialog(
                    onSave = {
                        showStopConfirmDialog = false
                        showSavePanel = true
                    },
                    onDiscard = {
                        showStopConfirmDialog = false
                        showSavePanel = false
                        routeName = ""
                        viewModel.resetToIdle()
                    },
                )
            }
        }
    }
}

private fun Context.hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun RecordingMapBackground(
    points: List<RoutePoint>,
    locationPermissionGranted: Boolean,
    onLocationError: (code: Int, message: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapLocationSource = remember {
        AMapMapLocationSource(
            context = context.applicationContext,
            onLocationError = onLocationError,
        )
    }
    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            map.uiSettings.isZoomControlsEnabled = false
            map.uiSettings.isMyLocationButtonEnabled = true
            map.setLocationSource(mapLocationSource)
            map.setMyLocationStyle(
                MyLocationStyle()
                    .myLocationType(MyLocationStyle.LOCATION_TYPE_FOLLOW)
                    .interval(MAP_LOCATION_INTERVAL_MS)
                    .strokeColor(android.graphics.Color.rgb(21, 101, 192))
                    .radiusFillColor(android.graphics.Color.argb(40, 21, 101, 192))
                    .strokeWidth(2f),
            )
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.map.isMyLocationEnabled = false
            mapLocationSource.deactivate()
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
            view.renderLiveRoute(
                points = points,
                currentLocationVisible = locationPermissionGranted,
            )
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveRecordingContent(
    points: List<RoutePoint>,
    pointCount: Int,
    distanceMeters: Double,
    elapsedTimeSeconds: Long,
    accuracy: Float?,
    latestPointAccepted: Boolean?,
    discardedPointCount: Int,
    warmupRemainingSeconds: Long,
    isPaused: Boolean,
    locationPermissionGranted: Boolean,
    onLocationError: (code: Int, message: String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    LaunchedEffect(isPaused) {
        if (isPaused) sheetState.expand()
    }
    BottomSheetScaffold(
        modifier = modifier,
        scaffoldState = scaffoldState,
        sheetPeekHeight = if (isPaused) 248.dp else 132.dp,
        sheetShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        sheetContainerColor = BeltColors.SportPanel,
        sheetContentColor = BeltColors.SportPanelText,
        sheetDragHandle = { BottomSheetDefaults.DragHandle(color = BeltColors.SportPanelLabel) },
        sheetContent = {
            if (isPaused) {
                PausedRecordingControls(
                    pointCount = pointCount,
                    distanceMeters = distanceMeters,
                    elapsedTimeSeconds = elapsedTimeSeconds,
                    onResume = onResume,
                    onFinish = onFinish,
                )
            } else {
                RecordingDetailsPanel(
                    pointCount = pointCount,
                    distanceMeters = distanceMeters,
                    elapsedTimeSeconds = elapsedTimeSeconds,
                    accuracy = accuracy,
                    latestPointAccepted = latestPointAccepted,
                    discardedPointCount = discardedPointCount,
                    warmupRemainingSeconds = warmupRemainingSeconds,
                    onPause = onPause,
                )
            }
        },
    ) { _ ->
        RecordingMapBackground(
            points = points,
            locationPermissionGranted = locationPermissionGranted,
            onLocationError = onLocationError,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun RecordingDetailsPanel(
    pointCount: Int,
    distanceMeters: Double,
    elapsedTimeSeconds: Long,
    accuracy: Float?,
    latestPointAccepted: Boolean?,
    discardedPointCount: Int,
    warmupRemainingSeconds: Long,
    onPause: () -> Unit,
) {
    val averageSpeedKmh = averageRecordedSpeedKmh(distanceMeters, elapsedTimeSeconds)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RecordMetric(
                label = "已保存点数",
                value = pointCount.toString(),
            )
            RecordMetric(
                label = "路程",
                value = distanceMeters.formatRecordingDistance(),
            )
        }
        Text(
            text = distanceMeters.formatRecordingDistance(),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (warmupRemainingSeconds > 0L) {
                "GPS 预热中 · ${warmupRemainingSeconds}s"
            } else {
                "正在记录路线"
            },
            color = BeltColors.SportGreen,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RecordDetail("记录时间", elapsedTimeSeconds.formatRecordingDuration())
            RecordDetail("平均速度", "%.1f km/h".format(Locale.getDefault(), averageSpeedKmh))
            RecordDetail("当前精度", accuracy?.let { "%.1f m".format(Locale.getDefault(), it) } ?: "--")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RecordDetail("定位状态", latestPointAccepted.formatAccuracyStatus())
            RecordDetail("已丢弃点数", discardedPointCount.toString())
        }
        Button(
            onClick = onPause,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = BeltColors.SportPanel,
            ),
        ) {
            Icon(Icons.Rounded.Pause, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("暂停", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PausedRecordingControls(
    pointCount: Int,
    distanceMeters: Double,
    elapsedTimeSeconds: Long,
    onResume: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("记录已暂停", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            text = "$pointCount 个点  ·  ${distanceMeters.formatRecordingDistance()}  ·  ${elapsedTimeSeconds.formatRecordingDuration()}",
            color = BeltColors.SportPanelLabel,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HoldToFinishRecordingButton(
                onFinish = onFinish,
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp),
            )
            Button(
                onClick = onResume,
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BeltColors.SportGreen),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.size(6.dp))
                Text("继续", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HoldToFinishRecordingButton(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    val currentOnFinish by rememberUpdatedState(onFinish)
    var holding by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .semantics {
                role = Role.Button
                contentDescription = "长按 1.5 秒结束记录"
                onLongClick(label = "结束记录") {
                    currentOnFinish()
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        progress.snapTo(0f)
                        holding = true
                        coroutineScope {
                            var completed = false
                            val holdJob = launch {
                                progress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = HOLD_TO_FINISH_DURATION_MS,
                                        easing = LinearEasing,
                                    ),
                                )
                                completed = true
                                holding = false
                                currentOnFinish()
                            }
                            try {
                                tryAwaitRelease()
                            } finally {
                                if (!completed) {
                                    holdJob.cancel()
                                    progress.snapTo(0f)
                                    holding = false
                                }
                            }
                        }
                    },
                )
            },
        shape = RoundedCornerShape(8.dp),
        color = BeltColors.StopRed.copy(alpha = 0.12f),
        contentColor = BeltColors.StopRed,
        border = BorderStroke(2.dp, BeltColors.StopRed),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress.value },
                        modifier = Modifier.size(38.dp),
                        color = BeltColors.StopRed,
                        trackColor = BeltColors.StopRed.copy(alpha = 0.2f),
                        strokeWidth = 4.dp,
                    )
                    Icon(
                        Icons.Rounded.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    if (holding) "继续按住" else "长按结束",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun RecordMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = BeltColors.SportPanelLabel, fontSize = 13.sp)
    }
}

@Composable
private fun RecordDetail(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = BeltColors.SportPanelLabel, fontSize = 12.sp)
    }
}

@Composable
private fun StopConfirmDialog(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("是否保存本次记录？") },
        text = { Text("选择“是”后进入保存界面并输入路线名称；选择“否”将放弃本次记录并返回初始记录界面。") },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("是")
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text("否")
            }
        },
    )
}

@Composable
private fun SaveRoutePanel(
    pointCount: Int,
    routeName: String,
    onRouteNameChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = BeltColors.SportPanel,
        contentColor = BeltColors.SportPanelText,
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .background(BeltColors.SportPanelSecondary, RoundedCornerShape(2.dp)),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("保存路线", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "已保存点数 $pointCount",
                    color = BeltColors.SportPanelLabel,
                    fontWeight = FontWeight.Bold,
                )
            }
            OutlinedTextField(
                value = routeName,
                onValueChange = onRouteNameChange,
                label = { Text("路线名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = BeltColors.SportPanelText,
                    unfocusedTextColor = BeltColors.SportPanelText,
                    focusedBorderColor = BeltColors.SportGreen,
                    unfocusedBorderColor = BeltColors.SportPanelLabel,
                    focusedLabelColor = BeltColors.SportGreen,
                    unfocusedLabelColor = BeltColors.SportPanelLabel,
                    cursorColor = BeltColors.SportGreen,
                ),
            )
            Button(
                onClick = onSave,
                enabled = routeName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BeltColors.SportGreen),
            ) {
                Text("保存路线")
            }
        }
    }
}

@Composable
private fun MainRecordButton(
    text: String,
    enabled: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (active) BeltColors.StopRed else BeltColors.SportGreen
    Box(
        modifier = modifier
            .size(88.dp)
            .background(color = color, shape = CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = if (active) 18.sp else 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun PermissionRequestCard(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("需要精确位置权限才能显示当前位置并记录路线")
            Button(onClick = onRequestPermission) {
                Text("授权精确位置")
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
internal fun RouteDetailScreen(
    route: RouteRecord,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val averageAccuracy = route.points
        .mapNotNull(RoutePoint::accuracy)
        .takeIf { it.isNotEmpty() }
        ?.average()
    val tangent = remember(route.points) {
        RouteTangentCalculator.getLatestTangent(route.points)
    }

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
        DataRow(
            label = "平均定位精度",
            value = averageAccuracy?.let { "%.1f 米".format(it) } ?: "--",
        )
        DataRow(
            label = "末端切线方向",
            value = tangent?.let {
                "${it.tangentBearingDegrees.toDirectionName()} %.1f°".format(it.tangentBearingDegrees)
            } ?: "至少需要 2 个有效点",
        )

        MapRoutePreview(points = route.points, tangent = tangent)
    }
}

@Composable
private fun MapRoutePreview(
    points: List<RoutePoint>,
    tangent: RouteTangent?,
) {
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
                    view.renderRoute(points, tangent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp),
            )
            Text(
                text = "蓝色为起点，红色为终点，绿色为路线，橙色箭头为末端切线方向。",
                style = MaterialTheme.typography.bodySmall,
            )
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

private fun Double.formatRecordingDistance(): String {
    return if (this >= 1_000.0) {
        "%.2f km".format(Locale.getDefault(), this / 1_000.0)
    } else {
        "%.0f m".format(Locale.getDefault(), this)
    }
}

private fun Long.formatRecordingDuration(): String {
    val hours = this / 3_600
    val minutes = this % 3_600 / 60
    val seconds = this % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(Locale.getDefault(), hours, minutes, seconds)
    } else {
        "%02d:%02d".format(Locale.getDefault(), minutes, seconds)
    }
}

private fun MapView.renderLiveRoute(
    points: List<RoutePoint>,
    currentLocationVisible: Boolean,
) {
    val aMap = map
    aMap.clear()

    val latLngs = points.map { LatLng(it.latitude, it.longitude) }
    if (latLngs.isEmpty()) {
        if (!currentLocationVisible) {
            aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_MAP_CENTER, 19f))
        }
        return
    }

    if (latLngs.size > 1) {
        aMap.addPolyline(
            PolylineOptions()
                .addAll(latLngs)
                .width(12f)
                .color(POLYLINE_COLOR),
        )
    }

    aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLngs.last(), 20f))
}

private fun MapView.renderRoute(
    points: List<RoutePoint>,
    tangent: RouteTangent?,
) {
    val aMap = map
    aMap.clear()

    val latLngs = points.map { LatLng(it.latitude, it.longitude) }
    if (latLngs.isEmpty()) {
        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_MAP_CENTER, 15f))
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

    val tangentEnd = tangent?.let {
        addTangentArrow(
            start = latLngs.last(),
            bearingDegrees = it.tangentBearingDegrees,
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
        tangentEnd?.let(builder::include)
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

private fun MapView.addTangentArrow(
    start: LatLng,
    bearingDegrees: Double,
): LatLng {
    val end = start.destinationPoint(bearingDegrees, TANGENT_LENGTH_METERS)
    val leftWing = end.destinationPoint(bearingDegrees + 150.0, TANGENT_ARROW_WING_METERS)
    val rightWing = end.destinationPoint(bearingDegrees - 150.0, TANGENT_ARROW_WING_METERS)
    val options = PolylineOptions()
        .width(16f)
        .color(TANGENT_COLOR)

    map.addPolyline(PolylineOptions().add(start, end).width(16f).color(TANGENT_COLOR))
    map.addPolyline(options.add(leftWing, end, rightWing))
    return end
}

private fun LatLng.destinationPoint(
    bearingDegrees: Double,
    distanceMeters: Double,
): LatLng {
    val angularDistance = distanceMeters / EARTH_RADIUS_METERS
    val bearing = Math.toRadians(bearingDegrees)
    val latitude = Math.toRadians(this.latitude)
    val longitude = Math.toRadians(this.longitude)
    val destinationLatitude = asin(
        sin(latitude) * cos(angularDistance) +
            cos(latitude) * sin(angularDistance) * cos(bearing),
    )
    val destinationLongitude = longitude + atan2(
        sin(bearing) * sin(angularDistance) * cos(latitude),
        cos(angularDistance) - sin(latitude) * sin(destinationLatitude),
    )
    return LatLng(Math.toDegrees(destinationLatitude), Math.toDegrees(destinationLongitude))
}

private fun Double.toDirectionName(): String {
    val directions = listOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")
    val index = ((this + 22.5) / 45.0).toInt() % directions.size
    return directions[index]
}

private const val START_HUE = 210f
private const val END_HUE = 0f
private const val ROUTE_HUE = 120f
private val DEFAULT_MAP_CENTER = LatLng(39.9928, 116.3109)
private val POLYLINE_COLOR = android.graphics.Color.rgb(46, 125, 50)
private val TANGENT_COLOR = android.graphics.Color.rgb(255, 109, 0)
private const val TINY_ROUTE_DEGREES = 0.001
private const val SMALL_ROUTE_DEGREES = 0.003
private const val MAX_VISIBLE_MARKERS = 80
private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val TANGENT_LENGTH_METERS = 30.0
private const val TANGENT_ARROW_WING_METERS = 8.0
private const val MAP_LOCATION_INTERVAL_MS = 3000L
private const val HOLD_TO_FINISH_DURATION_MS = 1_500
