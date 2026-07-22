package com.beltforblind.ui.record

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.beltforblind.ui.components.AppHeaderCard
import com.beltforblind.ui.components.StatusChip
import com.beltforblind.ui.components.StatusType
import com.beltforblind.ui.components.pressScale
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
    var mapLocationAccuracy by remember { mutableStateOf<Float?>(null) }
    var mapLocationTimestamp by remember { mutableStateOf<Long?>(null) }
    var showGpsDetails by remember { mutableStateOf(false) }
    var mapRecenterRequestId by remember { mutableLongStateOf(0L) }
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val isActivelyRecording =
                uiState is RecordingUiState.Recording || uiState is RecordingUiState.Paused
            if (isActivelyRecording && !showSavePanel && !showStopConfirmDialog) {
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
                    onMapLocation = { point ->
                        mapLocationAccuracy = point.accuracy
                        mapLocationTimestamp = point.timestamp
                    },
                    onGpsClick = { showGpsDetails = true },
                    recenterRequestId = mapRecenterRequestId,
                    onRecenter = { mapRecenterRequestId += 1L },
                    onPause = viewModel::pauseRecording,
                    onResume = viewModel::resumeRecording,
                    onFinish = {
                        viewModel.stopRecording()
                        showStopConfirmDialog = true
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                IdleRecordingContent(
                    points = recentPoints,
                    locationPermissionGranted = locationPermissionGranted,
                    showControls = !showSavePanel && !showStopConfirmDialog,
                    onLocationError = { code, message ->
                        mapLocationError = "定位失败（$code）：$message"
                    },
                    onMapLocation = { point ->
                        mapLocationAccuracy = point.accuracy
                        mapLocationTimestamp = point.timestamp
                    },
                    onGpsClick = { showGpsDetails = true },
                    recenterRequestId = mapRecenterRequestId,
                    onRecenter = { mapRecenterRequestId += 1L },
                    onStart = startRecording,
                    modifier = Modifier.fillMaxSize(),
                )

                if (showSavePanel) {
                    SaveRoutePanel(
                        pointCount = pointCount,
                        routeName = routeName,
                        onRouteNameChange = { routeName = it },
                        onSave = { viewModel.saveRoute(routeName) },
                        modifier = Modifier.align(Alignment.BottomCenter),
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

            if (showGpsDetails) {
                GpsAccuracyDialog(
                    status = when {
                        !locationPermissionGranted -> "GPS 未授权"
                        uiState is RecordingUiState.Paused -> "GPS 已暂停"
                        uiState is RecordingUiState.Recording && warmupRemainingSeconds > 0L -> {
                            "GPS 预热中（剩余 ${warmupRemainingSeconds}s）"
                        }
                        uiState is RecordingUiState.Recording -> "GPS 采集中"
                        else -> "GPS 待采集"
                    },
                    accuracy = mapLocationAccuracy ?: latestReceivedAccuracy,
                    updatedAt = mapLocationTimestamp,
                    onDismiss = { showGpsDetails = false },
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
private fun IdleRecordingContent(
    points: List<RoutePoint>,
    locationPermissionGranted: Boolean,
    showControls: Boolean,
    onLocationError: (code: Int, message: String) -> Unit,
    onMapLocation: (RoutePoint) -> Unit,
    onGpsClick: () -> Unit,
    recenterRequestId: Long,
    onRecenter: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        RecordingMapBackground(
            points = points,
            locationPermissionGranted = locationPermissionGranted,
            onLocationError = onLocationError,
            onLocation = onMapLocation,
            recenterRequestId = recenterRequestId,
            modifier = Modifier.fillMaxSize(),
        )
        RecordTopBar(
            active = false,
            gpsText = if (locationPermissionGranted) "GPS 待采集" else "GPS 未授权",
            gpsType = if (locationPermissionGranted) StatusType.Interactive else StatusType.Error,
            onGpsClick = onGpsClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        RecordRecenterButton(
            onClick = onRecenter,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 84.dp, end = 12.dp),
        )
        if (showControls) {
            RoundStartRecordingButton(
                enabled = true,
                onClick = onStart,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun RecordTopBar(
    active: Boolean,
    gpsText: String,
    gpsType: StatusType,
    onGpsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppHeaderCard(
        title = if (active) "路线记录中" else "路线记录",
        subtitle = "记录采样点与轨迹",
        leadingIcon = Icons.Rounded.Route,
        modifier = modifier,
    ) {
        StatusChip(
            icon = Icons.Rounded.GpsFixed,
            text = gpsText,
            type = gpsType,
            onClick = onGpsClick,
            contentDescription = "$gpsText，点击查看当前定位精度",
        )
    }
}

@Composable
private fun GpsAccuracyDialog(
    status: String,
    accuracy: Float?,
    updatedAt: Long?,
    onDismiss: () -> Unit,
) {
    val accuracyText = accuracy?.let {
        "±%.1f m".format(Locale.getDefault(), it)
    } ?: "等待定位结果"
    val qualityText = when {
        accuracy == null -> "尚无数据"
        accuracy <= 5f -> "优秀"
        accuracy <= RECORDING_ACCURACY_THRESHOLD_METERS -> "合格"
        accuracy <= 15f -> "一般"
        else -> "较弱"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.GpsFixed,
                contentDescription = null,
                tint = BeltColors.PrimaryPurple,
            )
        },
        title = { Text("GPS 定位详情") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DataRow(label = "定位状态", value = status)
                DataRow(label = "当前精度", value = accuracyText, boldValue = accuracy != null)
                DataRow(label = "精度质量", value = qualityText)
                DataRow(
                    label = "记录合格标准",
                    value = "≤ %.0f m".format(
                        Locale.getDefault(),
                        RECORDING_ACCURACY_THRESHOLD_METERS,
                    ),
                )
                DataRow(
                    label = "最近更新",
                    value = updatedAt?.let { timestamp ->
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
                    } ?: "--",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun RecordRecenterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier.pressScale(interactionSource),
        shape = CircleShape,
        color = BeltColors.Surface,
        contentColor = BeltColors.PrimaryPurple,
        shadowElevation = 3.dp,
    ) {
        IconButton(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier
                .size(44.dp)
                .semantics { contentDescription = "回到当前定位" },
        ) {
            Icon(Icons.Rounded.MyLocation, contentDescription = null)
        }
    }
}

@Composable
private fun RoundStartRecordingButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = CircleShape,
        color = BeltColors.PurpleContainer.copy(alpha = 0.82f),
        modifier = modifier
            .pressScale(interactionSource, pressedScale = 0.93f)
            .padding(2.dp),
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            modifier = Modifier
                .padding(6.dp)
                .size(88.dp),
            contentPadding = PaddingValues(0.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = BeltColors.PrimaryPurple,
                contentColor = Color.White,
                disabledContainerColor = BeltColors.Disabled,
            ),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("开始", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("记录", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun RecordingMapBackground(
    points: List<RoutePoint>,
    locationPermissionGranted: Boolean,
    onLocationError: (code: Int, message: String) -> Unit,
    onLocation: (RoutePoint) -> Unit,
    recenterRequestId: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnLocation by rememberUpdatedState(onLocation)
    var latestLocation by remember { mutableStateOf<RoutePoint?>(null) }
    var initialLocationCentered by remember { mutableStateOf(false) }
    var handledRecenterRequestId by remember {
        mutableLongStateOf(recenterRequestId)
    }
    val mapLocationSource = remember {
        AMapMapLocationSource(
            context = context.applicationContext,
            onLocationError = onLocationError,
            onLocation = { point ->
                latestLocation = point
                currentOnLocation(point)
            },
        )
    }
    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            map.uiSettings.isZoomControlsEnabled = false
            map.uiSettings.isZoomGesturesEnabled = false
            map.uiSettings.isMyLocationButtonEnabled = false
            map.moveCamera(CameraUpdateFactory.zoomTo(RECORD_MAP_ZOOM))
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

    LaunchedEffect(locationPermissionGranted, latestLocation) {
        if (!locationPermissionGranted) {
            initialLocationCentered = false
            return@LaunchedEffect
        }
        val point = latestLocation ?: return@LaunchedEffect
        if (initialLocationCentered) return@LaunchedEffect
        initialLocationCentered = true
        mapView.map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(point.latitude, point.longitude),
                RECORD_MAP_ZOOM,
            ),
        )
    }

    LaunchedEffect(recenterRequestId, latestLocation) {
        val point = latestLocation ?: return@LaunchedEffect
        if (recenterRequestId == handledRecenterRequestId) return@LaunchedEffect
        handledRecenterRequestId = recenterRequestId
        mapView.map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(point.latitude, point.longitude),
                RECORD_MAP_ZOOM,
            ),
        )
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
    onMapLocation: (RoutePoint) -> Unit,
    onGpsClick: () -> Unit,
    recenterRequestId: Long,
    onRecenter: () -> Unit,
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
        sheetPeekHeight = if (isPaused) 228.dp else 208.dp,
        sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        sheetContainerColor = BeltColors.Surface,
        sheetContentColor = BeltColors.TextPrimary,
        sheetDragHandle = { BottomSheetDefaults.DragHandle(color = BeltColors.Disabled) },
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
        Box(modifier = Modifier.fillMaxSize()) {
            RecordingMapBackground(
                points = points,
                locationPermissionGranted = locationPermissionGranted,
                onLocationError = onLocationError,
                onLocation = onMapLocation,
                recenterRequestId = recenterRequestId,
                modifier = Modifier.fillMaxSize(),
            )
            RecordTopBar(
                active = true,
                gpsText = when {
                    isPaused -> "GPS 已暂停"
                    warmupRemainingSeconds > 0L -> "GPS 预热 ${warmupRemainingSeconds}s"
                    else -> "GPS 采集中"
                },
                gpsType = when {
                    isPaused -> StatusType.Neutral
                    warmupRemainingSeconds > 0L -> StatusType.Warning
                    else -> StatusType.Success
                },
                onGpsClick = onGpsClick,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            RecordRecenterButton(
                onClick = onRecenter,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 84.dp, end = 12.dp),
            )
        }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = distanceMeters.formatRecordingDistance(),
                    fontSize = 40.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "总路程",
                    style = MaterialTheme.typography.bodySmall,
                    color = BeltColors.TextSecondary,
                )
            }
            StatusChip(
                icon = Icons.Rounded.GpsFixed,
                text = when {
                    warmupRemainingSeconds > 0L -> "预热 ${warmupRemainingSeconds}s"
                    latestPointAccepted == null -> "等待定位点"
                    latestPointAccepted == false -> "定位点已丢弃"
                    else -> "定位点已保存"
                },
                type = when {
                    warmupRemainingSeconds > 0L -> StatusType.Warning
                    latestPointAccepted == null -> StatusType.Neutral
                    latestPointAccepted == false -> StatusType.Error
                    else -> StatusType.Success
                },
            )
        }
        Button(
            onClick = onPause,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BeltColors.PrimaryPurple,
                contentColor = Color.White,
            ),
        ) {
            Icon(Icons.Rounded.Pause, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("暂停", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = BeltColors.Divider)
        Text(
            text = "详细数据",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RecordDetailTile(
                label = "记录时间",
                value = elapsedTimeSeconds.formatRecordingDuration(),
                icon = Icons.Rounded.Timer,
                modifier = Modifier.weight(1f),
            )
            RecordDetailTile(
                label = "当前精度",
                value = accuracy?.let { "%.1f m".format(Locale.getDefault(), it) } ?: "--",
                icon = Icons.Rounded.GpsFixed,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RecordDetailTile(
                label = "已保存点数",
                value = pointCount.toString(),
                icon = Icons.Rounded.Flag,
                modifier = Modifier.weight(1f),
            )
            RecordDetailTile(
                label = "已丢弃点数",
                value = discardedPointCount.toString(),
                icon = Icons.Rounded.Delete,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "最近定位：${latestPointAccepted.formatAccuracyStatus()}。上拉或下拉可查看记录数据。",
            style = MaterialTheme.typography.bodySmall,
            color = BeltColors.TextSecondary,
        )
    }
}

@Composable
private fun RecordDetailTile(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = BeltColors.Background,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = BeltColors.PurpleContainer,
                contentColor = BeltColors.PrimaryPurple,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(6.dp),
                )
            }
            Column {
                Text(label, style = MaterialTheme.typography.bodySmall, color = BeltColors.TextSecondary)
                Crossfade(
                    targetState = value,
                    animationSpec = tween(180),
                ) { animatedValue ->
                    Text(
                        animatedValue,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
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
            .padding(horizontal = 16.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusChip(
            icon = Icons.Rounded.Pause,
            text = "记录已暂停",
            type = StatusType.Warning,
        )
        Text(
            "记录已暂停",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "$pointCount 个点  ·  ${distanceMeters.formatRecordingDistance()}  ·  ${elapsedTimeSeconds.formatRecordingDuration()}",
            color = BeltColors.TextSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HoldToFinishRecordingButton(
                onFinish = onFinish,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp),
            )
            Button(
                onClick = onResume,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BeltColors.PrimaryPurple),
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
        shape = RoundedCornerShape(20.dp),
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
        color = BeltColors.Surface,
        contentColor = BeltColors.TextPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
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
                        .background(BeltColors.Disabled, RoundedCornerShape(2.dp)),
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
                    color = BeltColors.TextSecondary,
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
                    focusedTextColor = BeltColors.TextPrimary,
                    unfocusedTextColor = BeltColors.TextPrimary,
                    focusedBorderColor = BeltColors.PrimaryPurple,
                    unfocusedBorderColor = BeltColors.Disabled,
                    focusedLabelColor = BeltColors.PrimaryPurple,
                    unfocusedLabelColor = BeltColors.TextSecondary,
                    cursorColor = BeltColors.PrimaryPurple,
                ),
            )
            Button(
                onClick = onSave,
                enabled = routeName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BeltColors.PrimaryPurple),
            ) {
                Text("保存路线")
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
            .background(BeltColors.Background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "返回",
                style = MaterialTheme.typography.titleSmall,
                color = BeltColors.TextSecondary,
            )
        }

        Text(
            text = route.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = BeltColors.Surface,
            shadowElevation = 1.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                RouteDetailInfoRow(Icons.Rounded.Route, "路线 ID", route.id)
                HorizontalDivider(color = BeltColors.Divider)
                RouteDetailInfoRow(
                    Icons.Rounded.CalendarMonth,
                    "创建时间",
                    route.createdAt.formatTimestamp(),
                )
                HorizontalDivider(color = BeltColors.Divider)
                RouteDetailInfoRow(
                    Icons.Rounded.Flag,
                    "定位点数量",
                    route.points.size.toString(),
                )
                HorizontalDivider(color = BeltColors.Divider)
                RouteDetailInfoRow(
                    Icons.Rounded.GpsFixed,
                    "平均定位精度",
                    averageAccuracy?.let { "%.1f 米".format(it) } ?: "--",
                )
                HorizontalDivider(color = BeltColors.Divider)
                RouteDetailInfoRow(
                    Icons.Rounded.Navigation,
                    "末端切线方向",
                    tangent?.let {
                        "${it.tangentBearingDegrees.toDirectionName()} %.1f°"
                            .format(it.tangentBearingDegrees)
                    } ?: "至少需要 2 个有效点",
                )
            }
        }

        MapRoutePreview(points = route.points, tangent = tangent)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun RouteDetailInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BeltColors.PurpleContainer,
            contentColor = BeltColors.PrimaryPurple,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(8.dp).size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = BeltColors.TextSecondary,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = BeltColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = BeltColors.Surface,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Rounded.Map,
                    contentDescription = null,
                    tint = BeltColors.PrimaryPurple,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    "地图预览",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Surface(shape = RoundedCornerShape(14.dp)) {
                AndroidView(
                    factory = {
                        mapView.apply {
                            renderRoute(points, tangent)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                )
            }
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
            aMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(DEFAULT_MAP_CENTER, RECORD_MAP_ZOOM),
            )
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

    aMap.moveCamera(
        CameraUpdateFactory.newLatLngZoom(latLngs.last(), RECORD_MAP_ZOOM),
    )
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
                .color(DETAIL_ROUTE_COLOR),
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
private val DETAIL_ROUTE_COLOR = android.graphics.Color.rgb(113, 68, 199)
private val TANGENT_COLOR = android.graphics.Color.rgb(89, 50, 168)
private const val TINY_ROUTE_DEGREES = 0.001
private const val SMALL_ROUTE_DEGREES = 0.003
private const val MAX_VISIBLE_MARKERS = 80
private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val TANGENT_LENGTH_METERS = 30.0
private const val TANGENT_ARROW_WING_METERS = 8.0
private const val MAP_LOCATION_INTERVAL_MS = 3000L
private const val RECORD_MAP_ZOOM = 20f
private const val RECORDING_ACCURACY_THRESHOLD_METERS = 8f
private const val HOLD_TO_FINISH_DURATION_MS = 1_500
