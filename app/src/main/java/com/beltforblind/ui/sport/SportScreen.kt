package com.beltforblind.ui.sport

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.runtime.snapshotFlow
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beltforblind.motor.MotorConnectionStatus
import com.beltforblind.motor.MotorControlGateway
import com.beltforblind.navigation.heading.AndroidPhoneHeadingProvider
import com.beltforblind.navigation.heading.PhoneBackHeadingCalculator
import com.beltforblind.navigation.heading.PhoneHeadingSample
import com.beltforblind.navigation.heading.PhoneHeadingStatus
import com.beltforblind.navigation.vibration.NavigationMotorSignalPlanner
import com.beltforblind.navigation.vibration.NavigationVibrationStatus
import com.beltforblind.route.location.BeltGpsRoutePointMapper
import com.beltforblind.route.location.BeltPreferredLocationDataSource
import com.beltforblind.sport.background.SportBackgroundLock
import com.beltforblind.ui.components.AppHeaderCard
import com.beltforblind.ui.components.StatusChip
import com.beltforblind.ui.components.StatusType
import com.beltforblind.ui.components.pressScale
import com.beltforblind.ui.theme.BeltColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SportScreen(
    onOpenRecordPage: () -> Unit,
    motorController: MotorControlGateway,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SportViewModel = viewModel(
        factory = SportViewModel.factory(context.applicationContext),
    )
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val motorState by motorController.state.collectAsState()
    val beltGpsSample by motorController.gpsSample.collectAsState()
    var headingSample by remember { mutableStateOf(PhoneHeadingSample()) }
    val headingProvider = remember(context) {
        AndroidPhoneHeadingProvider(context.applicationContext) { sample ->
            headingSample = sample
        }
    }
    val requiredBeltPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        viewModel.onEvent(
            SportUiEvent.LocationPermissionChanged(
                grants[Manifest.permission.ACCESS_FINE_LOCATION] == true,
            ),
        )
    }
    val beltPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (requiredBeltPermissions.all { grants[it] == true }) {
            motorController.scanAndConnect()
        } else {
            viewModel.onEvent(
                SportUiEvent.BeltConnectionChanged(BeltConnectionState.Error),
            )
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    val requestBeltConnection = {
        if (motorState.status !in setOf(
                MotorConnectionStatus.Scanning,
                MotorConnectionStatus.Connecting,
                MotorConnectionStatus.Connected,
            )
        ) {
            val missingPermissions = requiredBeltPermissions.filter { permission ->
                ContextCompat.checkSelfPermission(context, permission) !=
                    PackageManager.PERMISSION_GRANTED
            }
            if (missingPermissions.isEmpty()) {
                motorController.scanAndConnect()
            } else {
                beltPermissionLauncher.launch(missingPermissions.toTypedArray())
            }
        }
    }
    val isActivelyRunning = state.stage in setOf(
        SportStage.RunningCollapsed,
        SportStage.RunningExpanded,
    )

    DisposableEffect(headingProvider) {
        onDispose {
            headingProvider.stop()
            SportBackgroundLock.stop(context)
        }
    }

    DisposableEffect(headingProvider, isActivelyRunning) {
        if (isActivelyRunning) headingProvider.start()
        onDispose {
            if (isActivelyRunning) headingProvider.stop()
        }
    }

    LaunchedEffect(motorState.status) {
        viewModel.onEvent(
            SportUiEvent.BeltConnectionChanged(motorState.status.toBeltConnectionState()),
        )
    }

    LaunchedEffect(beltGpsSample?.sequence, beltGpsSample?.receivedAtMillis) {
        val sample = beltGpsSample ?: return@LaunchedEffect
        val ageMillis = System.currentTimeMillis() - sample.receivedAtMillis
        if (ageMillis !in 0..BeltPreferredLocationDataSource.BELT_GPS_STALE_AFTER_MS) {
            return@LaunchedEffect
        }
        BeltGpsRoutePointMapper.toRoutePoint(sample)?.let { point ->
            viewModel.onEvent(SportUiEvent.BeltLocationUpdated(point))
        }
    }

    LaunchedEffect(isActivelyRunning, motorState.status) {
        if (!isActivelyRunning) return@LaunchedEffect
        if (motorState.status == MotorConnectionStatus.Error) {
            delay(BELT_RECONNECT_DELAY_MS)
        }
        if (motorState.status in setOf(
                MotorConnectionStatus.Disconnected,
                MotorConnectionStatus.Error,
            )
        ) {
            requestBeltConnection()
        }
    }

    LaunchedEffect(isActivelyRunning) {
        if (!isActivelyRunning) {
            SportBackgroundLock.stop(context)
            return@LaunchedEffect
        }

        runCatching {
            SportBackgroundLock.start(context)
        }.onFailure { error ->
            viewModel.onEvent(
                SportUiEvent.BackgroundLockFailed(
                    error.message ?: "系统拒绝启动后台运动服务",
                ),
            )
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(headingSample, state.currentLocation, isActivelyRunning) {
        val magneticHeading = headingSample.headingDegrees
        val point = state.currentLocation
        if (
            !isActivelyRunning ||
            headingSample.status != PhoneHeadingStatus.Available ||
            magneticHeading == null ||
            point == null
        ) {
            viewModel.onEvent(SportUiEvent.HeadingUnavailable)
            return@LaunchedEffect
        }

        val timestamp = point.timestamp.takeIf { it > 0L } ?: System.currentTimeMillis()
        val declination = GeomagneticField(
            point.latitude.toFloat(),
            point.longitude.toFloat(),
            0f,
            timestamp,
        ).declination.toDouble()
        viewModel.onEvent(
            SportUiEvent.HeadingUpdated(
                PhoneBackHeadingCalculator.toTrueNorth(
                    magneticHeadingDegrees = magneticHeading,
                    declinationDegrees = declination,
                ),
            ),
        )
    }

    val motorSignalPattern = remember(
        state.navigationVibrationDecision,
        isActivelyRunning,
        motorState.isConnected,
    ) {
        NavigationMotorSignalPlanner.patternFor(
            decision = state.navigationVibrationDecision,
            enabled = isActivelyRunning && motorState.isConnected,
        )
    }
    LaunchedEffect(
        motorSignalPattern,
        motorState.isConnected,
    ) {
        if (!motorState.isConnected) return@LaunchedEffect
        if (motorSignalPattern == null) {
            if (motorState.motorIntensities.any { it > 0 }) motorController.stopMotor()
            return@LaunchedEffect
        }

        try {
            do {
                motorSignalPattern.frames.forEach { frame ->
                    if (frame.isStopped) {
                        motorController.stopMotor()
                    } else {
                        motorController.setMotorIntensities(frame.motorIntensities)
                    }
                    delay(frame.durationMillis)
                }
            } while (motorSignalPattern.repeats)
        } finally {
            motorController.stopMotor()
        }
    }

    LaunchedEffect(Unit) {
        val permissionGranted = context.hasFineLocationPermission()
        viewModel.onEvent(SportUiEvent.LocationPermissionChanged(permissionGranted))
        if (!permissionGranted) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onEvent(SportUiEvent.DismissMessage)
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        if (state.isRoutePickerRequested) {
            RoutePickerScreen(
                routes = state.availableRoutes,
                selectedRouteId = state.selectedRoute?.id,
                loading = state.routesLoading,
                errorMessage = state.routeLoadError,
                onBack = { viewModel.onEvent(SportUiEvent.DismissRoutePicker) },
                onRetry = { viewModel.onEvent(SportUiEvent.ReloadRoutes) },
                onSelectRoute = { viewModel.onEvent(SportUiEvent.SelectRoute(it)) },
                onOpenRecordPage = {
                    viewModel.onEvent(SportUiEvent.DismissRoutePicker)
                    onOpenRecordPage()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
        } else when (state.stage) {
            SportStage.Preparing -> PreparingSportContent(
                state = state,
                onEvent = viewModel::onEvent,
                onBeltClick = requestBeltConnection,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
            SportStage.RunningCollapsed,
            SportStage.RunningExpanded,
            SportStage.Paused,
            -> RunningSportContent(
                state = state,
                onEvent = viewModel::onEvent,
                onBeltClick = requestBeltConnection,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
            SportStage.Finished -> FinishedSportContent(
                state = state,
                onEvent = viewModel::onEvent,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
        }
    }
}

@Composable
private fun PreparingSportContent(
    state: SportUiState,
    onEvent: (SportUiEvent) -> Unit,
    onBeltClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        SportMapLayer(state = state, onEvent = onEvent)

        SportTopBar(
            state = state,
            onGpsClick = { onEvent(SportUiEvent.OpenGpsStatus) },
            onGpsDismiss = { onEvent(SportUiEvent.DismissGpsStatus) },
            onBeltClick = onBeltClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SportRouteSelectionCard(
                state = state,
                onClick = { onEvent(SportUiEvent.OpenRoutePicker) },
            )
            RoundStartSportButton(
                enabled = state.canStart,
                onClick = { onEvent(SportUiEvent.StartRunning) },
            )
        }
    }
}

@Composable
private fun SportRouteSelectionCard(
    state: SportUiState,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = state.selectedRoute?.let { "已选择路线，${it.name}" }
                    ?: "选择跑步路线"
                role = Role.Button
            },
        color = BeltColors.Surface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BeltColors.PurpleContainer,
                contentColor = BeltColors.PrimaryPurple,
            ) {
                Icon(
                    Icons.Rounded.Route,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.selectedRoute?.name ?: "尚未选择路线",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.selectedRoute?.let {
                        "路线长度 ${it.totalDistanceMeters().formatDistance()}"
                    } ?: "点击选择一条已保存路线",
                    style = MaterialTheme.typography.bodySmall,
                    color = BeltColors.TextSecondary,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = BeltColors.PrimaryPurple,
            )
        }
    }
}

@Composable
private fun RoundStartSportButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = CircleShape,
        color = BeltColors.PurpleContainer.copy(alpha = 0.82f),
        modifier = Modifier.pressScale(interactionSource, pressedScale = 0.93f),
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            modifier = Modifier.padding(6.dp).size(92.dp),
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
                Text("GO", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunningSportContent(
    state: SportUiState,
    onEvent: (SportUiEvent) -> Unit,
    onBeltClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberStandardBottomSheetState(
        initialValue = if (state.stage == SportStage.RunningExpanded) {
            SheetValue.Expanded
        } else {
            SheetValue.PartiallyExpanded
        },
        skipHiddenState = true,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    LaunchedEffect(state.stage) {
        when (state.stage) {
            SportStage.RunningExpanded, SportStage.Paused -> sheetState.expand()
            SportStage.RunningCollapsed -> sheetState.partialExpand()
            else -> Unit
        }
    }
    LaunchedEffect(sheetState, state.stage) {
        snapshotFlow { sheetState.currentValue }
            .distinctUntilChanged()
            .collect { sheetValue ->
                if (state.stage == SportStage.Paused) return@collect
                onEvent(
                    if (sheetValue == SheetValue.Expanded) {
                        SportUiEvent.ExpandMetrics
                    } else {
                        SportUiEvent.CollapseMetrics
                    },
                )
            }
    }

    BottomSheetScaffold(
        modifier = modifier,
        scaffoldState = scaffoldState,
        sheetPeekHeight = if (state.stage == SportStage.Paused) 228.dp else 188.dp,
        sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        sheetContainerColor = BeltColors.Surface,
        sheetContentColor = BeltColors.TextPrimary,
        sheetDragHandle = { BottomSheetDefaults.DragHandle(color = BeltColors.Disabled) },
        sheetContent = {
            if (state.stage == SportStage.Paused) {
                PausedControls(state = state, onEvent = onEvent)
            } else {
                RunningMetrics(state = state, onEvent = onEvent)
            }
        },
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            SportMapLayer(state = state, onEvent = onEvent)
            SportTopBar(
                state = state,
                onGpsClick = { onEvent(SportUiEvent.OpenGpsStatus) },
                onGpsDismiss = { onEvent(SportUiEvent.DismissGpsStatus) },
                onBeltClick = onBeltClick,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            if (state.stage != SportStage.Paused) {
                RunningRouteStatusCard(
                    state = state,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 200.dp),
                )
            }
            RecenterButton(
                onClick = { onEvent(SportUiEvent.RecenterMap) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 284.dp),
            )
        }
    }
}

@Composable
private fun RunningRouteStatusCard(
    state: SportUiState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = BeltColors.Surface,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BeltColors.PurpleContainer,
                contentColor = BeltColors.PrimaryPurple,
            ) {
                Icon(
                    Icons.Rounded.Route,
                    contentDescription = null,
                    modifier = Modifier.padding(9.dp).size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.selectedRoute?.name ?: "路线未加载",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.navigationStatusText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = state.navigationStatusColor(),
                )
            }
        }
    }
}

@Composable
private fun SportMapLayer(
    state: SportUiState,
    onEvent: (SportUiEvent) -> Unit,
) {
    SportMap(
        route = state.selectedRoute,
        routeTangent = state.routeTangent,
        currentLocation = state.currentLocation,
        locationSourceKind = state.locationSource,
        locationPermissionGranted = state.locationPermissionGranted,
        followUser = state.isMapFollowingUser,
        recenterRequestId = state.recenterRequestId,
        onLocationUpdated = { onEvent(SportUiEvent.LocationUpdated(it)) },
        onLocationError = { code, message ->
            onEvent(SportUiEvent.LocationFailed(code, message))
        },
        onUserMapInteraction = { onEvent(SportUiEvent.MapMovedByUser) },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun SportTopBar(
    state: SportUiState,
    onGpsClick: () -> Unit,
    onGpsDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onBeltClick: (() -> Unit)? = null,
) {
    AppHeaderCard(
        title = "户外跑步",
        subtitle = "安全陪伴 · 科学运动",
        leadingIcon = Icons.AutoMirrored.Rounded.DirectionsRun,
        modifier = modifier,
    ) {
        GpsStatusMenu(
            gpsState = state.gpsState,
            locationSource = state.locationSource,
            expanded = state.isGpsStatusVisible,
            onClick = onGpsClick,
            onDismiss = onGpsDismiss,
        )
        if (onBeltClick != null) {
            BeltStatusChip(
                connectionState = state.beltConnectionState,
                motorIntensities = state.navigationVibrationDecision.motorIntensities,
                onClick = onBeltClick,
            )
        }
    }
}

@Composable
private fun BeltStatusChip(
    connectionState: BeltConnectionState,
    motorIntensities: List<Int>,
    onClick: (() -> Unit)?,
) {
    val connected = connectionState == BeltConnectionState.Connected
    val activeMotors = motorIntensities.mapIndexedNotNull { index, intensity ->
        (index + 1).takeIf { intensity > 0 }
    }
    val label = if (connected && activeMotors.isNotEmpty()) {
        "${connectionState.label} · ${activeMotors.joinToString("+")}号"
    } else {
        connectionState.label
    }
    val canRetry = connectionState in setOf(
        BeltConnectionState.Disconnected,
        BeltConnectionState.Error,
    )
    StatusChip(
        icon = Icons.Rounded.Bluetooth,
        text = label,
        type = when (connectionState) {
            BeltConnectionState.Connected -> StatusType.Success
            BeltConnectionState.Connecting -> StatusType.Warning
            BeltConnectionState.Disconnected -> StatusType.Neutral
            BeltConnectionState.Error -> StatusType.Error
        },
        onClick = if (canRetry) onClick else null,
        contentDescription = if (canRetry) "$label，点击重新连接" else label,
    )
}

@Composable
private fun GpsStatusMenu(
    gpsState: GpsState,
    locationSource: LocationSourceKind,
    expanded: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val statusType = when (gpsState.quality) {
        GpsQuality.Good -> StatusType.Success
        GpsQuality.Fair -> StatusType.Warning
        GpsQuality.Poor -> StatusType.Error
        GpsQuality.Unavailable -> StatusType.Neutral
    }
    val statusColor = when (statusType) {
        StatusType.Success -> BeltColors.Success
        StatusType.Warning -> BeltColors.Warning
        StatusType.Error -> BeltColors.Error
        else -> BeltColors.TextSecondary
    }
    Box {
        StatusChip(
            icon = Icons.Rounded.GpsFixed,
            text = "GPS ${gpsState.quality.label}",
            type = statusType,
            onClick = onClick,
            contentDescription = "GPS ${gpsState.quality.label}，${gpsState.formattedAccuracy()}，点击查看详情",
        )
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("GPS ${gpsState.quality.label}", fontWeight = FontWeight.Bold)
                Text(gpsState.formattedAccuracy())
                Text(
                    if (locationSource == LocationSourceKind.Belt) {
                        "定位来源：腰带 GPS"
                    } else {
                        "定位来源：手机 GPS"
                    },
                )
                Text(gpsState.signalBars(), color = statusColor)
            }
        }
    }
}

@Composable
private fun RunningMetrics(
    state: SportUiState,
    onEvent: (SportUiEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MetricValue("距离", state.distanceMeters.formatDistance())
            MetricValue("用时", state.elapsedTimeSeconds.formatDuration())
            MetricValue("平均速度", state.averageSpeedText())
        }
        Button(
            onClick = { onEvent(SportUiEvent.PauseRunning) },
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
    }
}

@Composable
private fun PausedControls(
    state: SportUiState,
    onEvent: (SportUiEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("运动已暂停", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            text = "${state.distanceMeters.formatDistance()}  ·  ${state.elapsedTimeSeconds.formatDuration()}",
            color = BeltColors.TextSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HoldToStopButton(
                holding = state.isHoldingToStop,
                onEvent = onEvent,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp),
            )
            Button(
                onClick = { onEvent(SportUiEvent.ResumeRunning) },
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
private fun HoldToStopButton(
    holding: Boolean,
    onEvent: (SportUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    val currentOnEvent by rememberUpdatedState(onEvent)
    Surface(
        modifier = modifier
            .semantics {
                role = Role.Button
                contentDescription = "长按 1.5 秒结束运动"
                onLongClick(label = "结束运动") {
                    currentOnEvent(SportUiEvent.ConfirmStop)
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        progress.snapTo(0f)
                        currentOnEvent(SportUiEvent.BeginHoldToStop)
                        coroutineScope {
                            var completed = false
                            val holdJob = launch {
                                progress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = HOLD_TO_STOP_DURATION_MS,
                                        easing = LinearEasing,
                                    ),
                                )
                                completed = true
                                currentOnEvent(SportUiEvent.ConfirmStop)
                            }
                            try {
                                tryAwaitRelease()
                            } finally {
                                if (!completed) {
                                    holdJob.cancel()
                                    progress.snapTo(0f)
                                    currentOnEvent(SportUiEvent.CancelHoldToStop)
                                }
                            }
                        }
                    },
                )
            },
        shape = RoundedCornerShape(16.dp),
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
private fun MetricValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Crossfade(
            targetState = value,
            animationSpec = tween(180),
        ) { animatedValue ->
            Text(text = animatedValue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Text(text = label, color = BeltColors.TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun RecenterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier.pressScale(interactionSource),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        IconButton(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier
                .size(56.dp)
                .semantics { contentDescription = "重新定位并恢复地图跟随" },
        ) {
            Icon(Icons.Rounded.MyLocation, contentDescription = null)
        }
    }
}

@Composable
private fun FinishedSportContent(
    state: SportUiState,
    onEvent: (SportUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        SportMapLayer(state = state, onEvent = onEvent)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = BeltColors.Surface,
            contentColor = BeltColors.TextPrimary,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "运动已结束",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text("${state.distanceMeters.formatDistance()}  ·  ${state.elapsedTimeSeconds.formatDuration()}")
                Button(
                    onClick = { onEvent(SportUiEvent.ReturnToPreparing) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BeltColors.PrimaryPurple),
                ) {
                    Text("返回运动首页", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun Context.hasFineLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
}

private fun MotorConnectionStatus.toBeltConnectionState(): BeltConnectionState {
    return when (this) {
        MotorConnectionStatus.Disconnected -> BeltConnectionState.Disconnected
        MotorConnectionStatus.Scanning,
        MotorConnectionStatus.Connecting,
        -> BeltConnectionState.Connecting
        MotorConnectionStatus.Connected -> BeltConnectionState.Connected
        MotorConnectionStatus.Error -> BeltConnectionState.Error
    }
}

private fun GpsState.formattedAccuracy(): String {
    return accuracyMeters?.let { "±%.0f m".format(Locale.getDefault(), it) } ?: "暂无精度"
}

private fun GpsState.signalBars(): String {
    return when (quality) {
        GpsQuality.Good -> "▮ ▮ ▮"
        GpsQuality.Fair -> "▮ ▮ □"
        GpsQuality.Poor -> "▮ □ □"
        GpsQuality.Unavailable -> "□ □ □"
    }
}

private fun Double.formatDistance(): String {
    return if (this < 1_000.0) {
        "%.0f m".format(Locale.getDefault(), this)
    } else {
        "%.2f km".format(Locale.getDefault(), this / 1_000.0)
    }
}

private fun SportUiState.averageSpeedText(): String {
    val speedKmh = if (elapsedTimeSeconds <= 0L) {
        0.0
    } else {
        distanceMeters / elapsedTimeSeconds * 3.6
    }
    return "%.1f km/h".format(Locale.getDefault(), speedKmh)
}

private fun Long.formatDuration(): String {
    val hours = this / 3_600
    val minutes = this % 3_600 / 60
    val seconds = this % 60
    return "%02d:%02d:%02d".format(Locale.getDefault(), hours, minutes, seconds)
}

private fun SportUiState.startRequirementText(): String {
    return when {
        selectedRoute == null -> "请选择一条已保存路线"
        selectedRoute.points.size < MIN_ROUTE_POINT_COUNT -> "所选路线没有足够的有效路径点"
        !locationPermissionGranted -> "需要精确位置权限"
        currentLocation == null -> "正在等待有效定位"
        else -> "路线和定位已就绪"
    }
}

private fun SportUiState.navigationStatusText(): String {
    val progress = (routeProgress * 100).toInt()
    return when (navigationVibrationDecision.status) {
        NavigationVibrationStatus.Guiding -> {
            val activeMotors = navigationVibrationDecision.motorIntensities
                .mapIndexedNotNull { index, intensity ->
                    (index + 1).takeIf { intensity > 0 }
                }
                .joinToString("+")
                .ifEmpty { "-" }
            "方向引导中 · $activeMotors 号电机 · ${progress}%"
        }
        NavigationVibrationStatus.OffRoute -> "已偏离路线 · 左右双振提醒"
        NavigationVibrationStatus.Arrived -> "已到达终点 · 三次短振"
        NavigationVibrationStatus.UnreliableLocation -> "定位精度不足 · 震动已暂停"
        NavigationVibrationStatus.AwaitingHeading -> "正在获取手机朝向 · 震动已暂停"
        NavigationVibrationStatus.RouteUnavailable -> "暂时无法匹配路线"
        NavigationVibrationStatus.Inactive -> "路线引导准备中"
    }
}

private fun SportUiState.navigationStatusColor(): Color {
    return when (navigationVibrationDecision.status) {
        NavigationVibrationStatus.Guiding,
        NavigationVibrationStatus.Arrived,
        -> BeltColors.Success
        NavigationVibrationStatus.OffRoute,
        NavigationVibrationStatus.UnreliableLocation,
        -> BeltColors.Error
        NavigationVibrationStatus.AwaitingHeading,
        NavigationVibrationStatus.RouteUnavailable,
        NavigationVibrationStatus.Inactive,
        -> BeltColors.TextSecondary
    }
}

private const val HOLD_TO_STOP_DURATION_MS = 1_500
private const val BELT_RECONNECT_DELAY_MS = 3_000L
