package com.beltforblind.ui.sport

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Bluetooth
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beltforblind.ui.theme.BeltColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SportScreen(
    onOpenRecordPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SportViewModel = viewModel(
        factory = SportViewModel.factory(context.applicationContext),
    )
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        viewModel.onEvent(
            SportUiEvent.LocationPermissionChanged(
                grants[Manifest.permission.ACCESS_FINE_LOCATION] == true,
            ),
        )
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
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        SportMapLayer(state = state, onEvent = onEvent)

        SportTopBar(
            state = state,
            onGpsClick = { onEvent(SportUiEvent.OpenGpsStatus) },
            onGpsDismiss = { onEvent(SportUiEvent.DismissGpsStatus) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 3.dp,
            ) {
                OutlinedButton(
                    onClick = { onEvent(SportUiEvent.OpenRoutePicker) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .semantics {
                            contentDescription = state.selectedRoute?.let { "已选择路线，${it.name}" }
                                ?: "选择跑步路线"
                        },
                ) {
                    Icon(Icons.Rounded.Route, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(state.selectedRoute?.name ?: "选择路线")
                }
            }

            Text(
                text = state.startRequirementText(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = state.startRequirementText() },
            )

            Button(
                onClick = { onEvent(SportUiEvent.StartRunning) },
                enabled = state.canStart,
                modifier = Modifier
                    .size(104.dp)
                    .semantics {
                        contentDescription = if (state.canStart) {
                            "开始户外跑步"
                        } else {
                            "开始按钮不可用，需要路线、定位权限和有效定位"
                        }
                    },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = BeltColors.SportGreen),
            ) {
                Text(
                    text = "GO",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunningSportContent(
    state: SportUiState,
    onEvent: (SportUiEvent) -> Unit,
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
        sheetPeekHeight = if (state.stage == SportStage.Paused) 248.dp else 132.dp,
        sheetShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        sheetContainerColor = BeltColors.SportPanel,
        sheetContentColor = BeltColors.SportPanelText,
        sheetDragHandle = { BottomSheetDefaults.DragHandle(color = BeltColors.SportPanelLabel) },
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
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
            )
            RecenterButton(
                onClick = { onEvent(SportUiEvent.RecenterMap) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 148.dp),
            )
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
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.DirectionsRun, contentDescription = null)
                Text(
                    text = "户外跑步",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GpsStatusMenu(
                gpsState = state.gpsState,
                expanded = state.isGpsStatusVisible,
                onClick = onGpsClick,
                onDismiss = onGpsDismiss,
            )
            if (state.stage != SportStage.Preparing) {
                BeltStatusChip(state.beltConnectionState)
            }
        }
    }
}

@Composable
private fun BeltStatusChip(connectionState: BeltConnectionState) {
    val connected = connectionState == BeltConnectionState.Connected
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentColor = if (connected) BeltColors.SportGreenDark else MaterialTheme.colorScheme.outline,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.semantics {
            contentDescription = connectionState.label
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Bluetooth,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(connectionState.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun GpsStatusMenu(
    gpsState: GpsState,
    expanded: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val statusColor = when (gpsState.quality) {
        GpsQuality.Good -> BeltColors.SportGreenDark
        GpsQuality.Fair -> BeltColors.GpsWarning
        GpsQuality.Poor -> BeltColors.StopRed
        GpsQuality.Unavailable -> MaterialTheme.colorScheme.outline
    }
    Box {
        FilledTonalButton(
            onClick = onClick,
            modifier = Modifier
                .height(52.dp)
                .semantics {
                    contentDescription = "GPS ${gpsState.quality.label}，${gpsState.formattedAccuracy()}"
                },
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                contentColor = statusColor,
            ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Icon(Icons.Rounded.GpsFixed, contentDescription = null)
            Spacer(modifier = Modifier.size(6.dp))
            Text(gpsState.quality.label, fontWeight = FontWeight.Bold)
        }
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
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MetricValue("总距离", state.distanceMeters.formatDistance())
            MetricValue("总时长", state.elapsedTimeSeconds.formatDuration())
            MetricValue("实时配速", state.paceSecondsPerKilometer.formatPace())
        }
        Text(
            text = state.distanceMeters.formatDistance(),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("路线完成", color = BeltColors.SportPanelLabel)
                Text("${(state.routeProgress * 100).toInt()}%", fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { state.routeProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .semantics {
                        contentDescription = "路线已完成 ${(state.routeProgress * 100).toInt()}%"
                    },
                color = BeltColors.SportGreen,
                trackColor = BeltColors.SportPanelSecondary,
            )
        }
        Button(
            onClick = { onEvent(SportUiEvent.PauseRunning) },
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
private fun PausedControls(
    state: SportUiState,
    onEvent: (SportUiEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("运动已暂停", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            text = "${state.distanceMeters.formatDistance()}  ·  ${state.elapsedTimeSeconds.formatDuration()}",
            color = BeltColors.SportPanelLabel,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HoldToStopButton(
                holding = state.isHoldingToStop,
                onEvent = onEvent,
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp),
            )
            Button(
                onClick = { onEvent(SportUiEvent.ResumeRunning) },
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
private fun MetricValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = BeltColors.SportPanelLabel, fontSize = 13.sp)
    }
}

@Composable
private fun RecenterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        IconButton(
            onClick = onClick,
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
            color = BeltColors.SportPanel,
            contentColor = BeltColors.SportPanelText,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
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
                    colors = ButtonDefaults.buttonColors(containerColor = BeltColors.SportGreen),
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
    return "%.2f km".format(Locale.getDefault(), this / 1_000.0)
}

private fun Long.formatDuration(): String {
    val hours = this / 3_600
    val minutes = this % 3_600 / 60
    val seconds = this % 60
    return "%02d:%02d:%02d".format(Locale.getDefault(), hours, minutes, seconds)
}

private fun Long?.formatPace(): String {
    if (this == null) return "--"
    return "%d'%02d\"".format(Locale.getDefault(), this / 60, this % 60)
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

private const val HOLD_TO_STOP_DURATION_MS = 1_500
