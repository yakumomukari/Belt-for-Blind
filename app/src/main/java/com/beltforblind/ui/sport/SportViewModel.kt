package com.beltforblind.ui.sport

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.beltforblind.navigation.vibration.NavigationInputFreshness
import com.beltforblind.navigation.vibration.NavigationInputFreshnessPolicy
import com.beltforblind.navigation.vibration.NavigationVibrationDecision
import com.beltforblind.navigation.vibration.NavigationVibrationPlanner
import com.beltforblind.navigation.vibration.NavigationVibrationStatus
import com.beltforblind.route.model.RoutePoint
import com.beltforblind.route.model.RouteRecord
import com.beltforblind.route.storage.JsonRouteStore
import com.beltforblind.route.storage.RouteStore
import com.beltforblind.route.tangent.RouteTangent
import com.beltforblind.route.tangent.RouteTangentCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SportViewModel(
    private val routeStore: RouteStore,
    private val monotonicClockMillis: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SportUiState())
    val uiState: StateFlow<SportUiState> = mutableUiState.asStateFlow()

    private var timerJob: Job? = null
    private var stageBeforePause = SportStage.RunningExpanded
    private val metricsAccumulator = SportMetricsAccumulator()
    private var lastLocationUpdateMillis: Long? = null
    private var lastBeltLocationUpdateMillis: Long? = null
    private var lastHeadingUpdateMillis: Long? = null

    init {
        loadRoutes()
    }

    fun onEvent(event: SportUiEvent) {
        when (event) {
            SportUiEvent.OpenGpsStatus -> update { copy(isGpsStatusVisible = true) }
            SportUiEvent.DismissGpsStatus -> update { copy(isGpsStatusVisible = false) }
            SportUiEvent.OpenRoutePicker -> {
                update { copy(isRoutePickerRequested = true) }
                loadRoutes()
            }
            SportUiEvent.ReloadRoutes -> loadRoutes()
            is SportUiEvent.SelectRoute -> update {
                copy(
                    selectedRoute = event.route,
                    isRoutePickerRequested = false,
                    routeProgress = 0f,
                    routeTangent = null,
                    navigationVibrationDecision = NavigationVibrationDecision(),
                )
            }
            SportUiEvent.DismissRoutePicker -> update { copy(isRoutePickerRequested = false) }
            SportUiEvent.StartRunning -> startRunning()
            SportUiEvent.ExpandMetrics -> updateRunningStage(SportStage.RunningExpanded)
            SportUiEvent.CollapseMetrics -> updateRunningStage(SportStage.RunningCollapsed)
            SportUiEvent.PauseRunning -> pauseRunning()
            SportUiEvent.ResumeRunning -> resumeRunning()
            SportUiEvent.BeginHoldToStop -> beginHoldToStop()
            SportUiEvent.CancelHoldToStop -> cancelHoldToStop()
            SportUiEvent.ConfirmStop -> finishRunning()
            SportUiEvent.ReturnToPreparing -> returnToPreparing()
            SportUiEvent.RecenterMap -> update {
                copy(
                    isMapFollowingUser = true,
                    recenterRequestId = recenterRequestId + 1,
                )
            }
            SportUiEvent.MapMovedByUser -> update { copy(isMapFollowingUser = false) }
            is SportUiEvent.LocationPermissionChanged -> onLocationPermissionChanged(event.granted)
            is SportUiEvent.LocationUpdated -> onPhoneLocationUpdated(event.point)
            is SportUiEvent.BeltLocationUpdated -> onBeltLocationUpdated(event.point)
            is SportUiEvent.LocationFailed -> onLocationFailed(event.code, event.message)
            is SportUiEvent.HeadingUpdated -> onHeadingUpdated(event.headingDegrees)
            SportUiEvent.HeadingUnavailable -> onHeadingUnavailable()
            is SportUiEvent.BeltConnectionChanged -> update {
                copy(beltConnectionState = event.state)
            }
            is SportUiEvent.BackgroundLockFailed -> update {
                copy(message = "后台运行锁启动失败：${event.message}")
            }
            SportUiEvent.DismissMessage -> update { copy(message = null) }
        }
    }

    private fun onPhoneLocationUpdated(point: RoutePoint) {
        val nowMillis = monotonicClockMillis()
        val beltUpdate = lastBeltLocationUpdateMillis
        if (beltUpdate != null && nowMillis - beltUpdate <= BELT_GPS_STALE_AFTER_MS) return
        onLocationUpdated(point, LocationSourceKind.Phone, nowMillis)
    }

    private fun onBeltLocationUpdated(point: RoutePoint) {
        val nowMillis = monotonicClockMillis()
        lastBeltLocationUpdateMillis = nowMillis
        onLocationUpdated(point, LocationSourceKind.Belt, nowMillis)
    }

    private fun onLocationUpdated(
        point: RoutePoint,
        source: LocationSourceKind,
        nowMillis: Long,
    ) {
        lastLocationUpdateMillis = nowMillis
        val state = mutableUiState.value
        if (state.stage !in RUNNING_STAGES) {
            update {
                copy(
                    currentLocation = point,
                    gpsState = GpsState.from(point),
                    locationSource = source,
                )
            }
            return
        }

        val metrics = metricsAccumulator.add(point)
        val rawTangent = state.selectedRoute?.let { route ->
            RouteTangentCalculator.getTangent(route.points, point)
        }
        val candidate = findAcceptedRouteTangent(state.selectedRoute, point)
            ?.takeIf { it.progress >= state.routeProgress.toDouble() }
        val vibrationDecision = NavigationVibrationPlanner.plan(
            tangent = rawTangent,
            headingDegrees = freshHeadingDegrees(state, nowMillis),
            locationAccuracyMeters = point.accuracy,
            previousGuidanceAngleDegrees =
                state.navigationVibrationDecision.guidanceAngleDegrees,
        )
        update {
            copy(
                currentLocation = point,
                gpsState = GpsState.from(point),
                locationSource = source,
                distanceMeters = metrics.distanceMeters,
                paceSecondsPerKilometer = metrics.paceSecondsPerKilometer,
                routeProgress = candidate?.progress?.toFloat() ?: routeProgress,
                routeTangent = candidate ?: routeTangent,
                navigationVibrationDecision = vibrationDecision,
            )
        }
    }

    private fun onHeadingUpdated(headingDegrees: Double) {
        if (!headingDegrees.isFinite()) {
            onHeadingUnavailable()
            return
        }
        lastHeadingUpdateMillis = monotonicClockMillis()
        val state = mutableUiState.value.copy(headingDegrees = headingDegrees.normalize360())
        mutableUiState.value = state.copy(
            navigationVibrationDecision = planNavigation(state),
        )
    }

    private fun onHeadingUnavailable() {
        lastHeadingUpdateMillis = null
        val state = mutableUiState.value.copy(headingDegrees = null)
        mutableUiState.value = state.copy(
            navigationVibrationDecision = planNavigation(state),
        )
    }

    private fun planNavigation(state: SportUiState): NavigationVibrationDecision {
        if (state.stage !in RUNNING_STAGES) return NavigationVibrationDecision()
        val nowMillis = monotonicClockMillis()
        if (inputFreshness(nowMillis) == NavigationInputFreshness.LocationStale) {
            return staleLocationDecision(state.navigationVibrationDecision)
        }
        if (state.gpsState.quality == GpsQuality.Unavailable || state.gpsState.errorMessage != null) {
            return NavigationVibrationDecision(
                status = NavigationVibrationStatus.UnreliableLocation,
            )
        }
        val point = state.currentLocation
            ?: return NavigationVibrationDecision(
                status = NavigationVibrationStatus.UnreliableLocation,
            )
        val tangent = state.selectedRoute?.let { route ->
            RouteTangentCalculator.getTangent(route.points, point)
        }
        return NavigationVibrationPlanner.plan(
            tangent = tangent,
            headingDegrees = freshHeadingDegrees(state, nowMillis),
            locationAccuracyMeters = point.accuracy,
            previousGuidanceAngleDegrees =
                state.navigationVibrationDecision.guidanceAngleDegrees,
        )
    }

    private fun findAcceptedRouteTangent(
        route: RouteRecord?,
        point: RoutePoint,
    ): RouteTangent? {
        val accuracy = point.accuracy
        if (route == null || accuracy == null || accuracy <= 0f || accuracy > MAX_PROGRESS_ACCURACY_METERS) {
            return null
        }
        val tangent = RouteTangentCalculator.getTangent(route.points, point) ?: return null
        val allowedDistance = maxOf(MIN_ROUTE_MATCH_DISTANCE_METERS, accuracy * 2.0)
        return tangent.takeIf { it.distanceToRouteMeters <= allowedDistance }
    }

    private fun loadRoutes() {
        update { copy(routesLoading = true, routeLoadError = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching(routeStore::loadAll)
            }
            result.onSuccess { routes ->
                update {
                    val refreshedSelection = selectedRoute?.let { selected ->
                        routes.firstOrNull { it.id == selected.id }
                    }
                    copy(
                        availableRoutes = routes,
                        selectedRoute = refreshedSelection,
                        routesLoading = false,
                        routeLoadError = null,
                    )
                }
            }.onFailure {
                update {
                    copy(
                        routesLoading = false,
                        routeLoadError = "路线读取失败，请重试",
                    )
                }
            }
        }
    }

    private fun startRunning() {
        val state = mutableUiState.value
        if (!state.canStart) {
            val reason = when {
                state.selectedRoute == null -> "请先选择一条路线"
                !state.locationPermissionGranted -> "需要定位权限才能开始运动"
                else -> "正在等待有效定位"
            }
            update { copy(message = reason) }
            return
        }
        val nowMillis = monotonicClockMillis()
        if (inputFreshness(nowMillis) == NavigationInputFreshness.LocationStale) {
            update { copy(message = "定位数据已超时，正在等待新的定位点") }
            return
        }

        metricsAccumulator.reset(state.currentLocation)
        val rawTangent = state.currentLocation?.let { point ->
            state.selectedRoute?.let { route ->
                RouteTangentCalculator.getTangent(route.points, point)
            }
        }
        val initialTangent = state.currentLocation?.let { point ->
            findAcceptedRouteTangent(state.selectedRoute, point)
        }
        val vibrationDecision = NavigationVibrationPlanner.plan(
            tangent = rawTangent,
            headingDegrees = freshHeadingDegrees(state, nowMillis),
            locationAccuracyMeters = state.currentLocation?.accuracy,
            previousGuidanceAngleDegrees =
                state.navigationVibrationDecision.guidanceAngleDegrees,
        )
        update {
            copy(
                stage = SportStage.RunningCollapsed,
                elapsedTimeSeconds = 0L,
                distanceMeters = 0.0,
                paceSecondsPerKilometer = null,
                routeProgress = initialTangent?.progress?.toFloat() ?: 0f,
                routeTangent = initialTangent,
                navigationVibrationDecision = vibrationDecision,
                isHoldingToStop = false,
                message = null,
            )
        }
        startTimer()
    }

    private fun updateRunningStage(stage: SportStage) {
        if (mutableUiState.value.stage in RUNNING_STAGES) {
            update { copy(stage = stage) }
        }
    }

    private fun pauseRunning() {
        val currentStage = mutableUiState.value.stage
        if (currentStage !in RUNNING_STAGES) return
        stageBeforePause = currentStage
        timerJob?.cancel()
        metricsAccumulator.pause()
        update {
            copy(
                stage = SportStage.Paused,
                paceSecondsPerKilometer = null,
                isHoldingToStop = false,
                navigationVibrationDecision = NavigationVibrationDecision(),
            )
        }
    }

    private fun resumeRunning() {
        if (mutableUiState.value.stage != SportStage.Paused) return
        metricsAccumulator.resumeAt(mutableUiState.value.currentLocation)
        update {
            val resumed = copy(
                stage = stageBeforePause,
                paceSecondsPerKilometer = null,
                isHoldingToStop = false,
            )
            resumed.copy(navigationVibrationDecision = planNavigation(resumed))
        }
        startTimer()
    }

    private fun finishRunning() {
        if (mutableUiState.value.stage != SportStage.Paused) return
        timerJob?.cancel()
        update {
            copy(
                stage = SportStage.Finished,
                isHoldingToStop = false,
                navigationVibrationDecision = NavigationVibrationDecision(),
            )
        }
    }

    private fun beginHoldToStop() {
        if (mutableUiState.value.stage == SportStage.Paused) {
            update { copy(isHoldingToStop = true) }
        }
    }

    private fun cancelHoldToStop() {
        if (mutableUiState.value.stage == SportStage.Paused) {
            update { copy(isHoldingToStop = false) }
        }
    }

    private fun returnToPreparing() {
        if (mutableUiState.value.stage != SportStage.Finished) return
        timerJob?.cancel()
        metricsAccumulator.reset(null)
        stageBeforePause = SportStage.RunningExpanded
        update {
            copy(
                stage = SportStage.Preparing,
                elapsedTimeSeconds = 0L,
                distanceMeters = 0.0,
                paceSecondsPerKilometer = null,
                routeProgress = 0f,
                routeTangent = null,
                navigationVibrationDecision = NavigationVibrationDecision(),
                isHoldingToStop = false,
                message = null,
            )
        }
    }

    private fun onLocationPermissionChanged(granted: Boolean) {
        if (!granted) lastLocationUpdateMillis = null
        update {
            copy(
                locationPermissionGranted = granted,
                gpsState = if (granted) gpsState else GpsState(),
                currentLocation = if (granted) currentLocation else null,
                navigationVibrationDecision = if (granted) {
                    navigationVibrationDecision
                } else if (stage in RUNNING_STAGES) {
                    NavigationVibrationDecision(
                        status = NavigationVibrationStatus.UnreliableLocation,
                    )
                } else {
                    NavigationVibrationDecision()
                },
            )
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive && mutableUiState.value.stage in RUNNING_STAGES) {
                delay(1_000L)
                val freshness = inputFreshness(monotonicClockMillis())
                update {
                    copy(
                        elapsedTimeSeconds = elapsedTimeSeconds + 1L,
                        navigationVibrationDecision = when (freshness) {
                            NavigationInputFreshness.LocationStale -> {
                                staleLocationDecision(navigationVibrationDecision)
                            }
                            NavigationInputFreshness.HeadingStale -> {
                                if (navigationVibrationDecision.status == NavigationVibrationStatus.Guiding) {
                                    navigationVibrationDecision.copy(
                                        status = NavigationVibrationStatus.AwaitingHeading,
                                        motorNumber = null,
                                        motorIntensities = ZERO_MOTOR_INTENSITIES,
                                        relativeBearingDegrees = null,
                                        guidanceAngleDegrees = null,
                                    )
                                } else {
                                    navigationVibrationDecision
                                }
                            }
                            NavigationInputFreshness.Fresh -> navigationVibrationDecision
                        },
                    )
                }
            }
        }
    }

    private fun onLocationFailed(code: Int, message: String) {
        val beltUpdate = lastBeltLocationUpdateMillis
        if (
            beltUpdate != null &&
            monotonicClockMillis() - beltUpdate <= BELT_GPS_STALE_AFTER_MS
        ) {
            return
        }
        lastLocationUpdateMillis = null
        update {
            copy(
                gpsState = GpsState(
                    quality = GpsQuality.Unavailable,
                    errorMessage = message,
                ),
                navigationVibrationDecision = if (stage in RUNNING_STAGES) {
                    staleLocationDecision(navigationVibrationDecision)
                } else {
                    NavigationVibrationDecision()
                },
                message = "定位失败（$code）：$message",
            )
        }
    }

    private fun inputFreshness(nowMillis: Long): NavigationInputFreshness {
        return NavigationInputFreshnessPolicy.evaluate(
            nowMillis = nowMillis,
            lastLocationUpdateMillis = lastLocationUpdateMillis,
            lastHeadingUpdateMillis = lastHeadingUpdateMillis,
        )
    }

    private fun freshHeadingDegrees(state: SportUiState, nowMillis: Long): Double? {
        return state.headingDegrees.takeIf {
            inputFreshness(nowMillis) == NavigationInputFreshness.Fresh
        }
    }

    private fun staleLocationDecision(
        current: NavigationVibrationDecision,
    ): NavigationVibrationDecision {
        return current.copy(
            status = NavigationVibrationStatus.UnreliableLocation,
            motorNumber = null,
            motorIntensities = ZERO_MOTOR_INTENSITIES,
            relativeBearingDegrees = null,
            guidanceAngleDegrees = null,
        )
    }

    private inline fun update(transform: SportUiState.() -> SportUiState) {
        mutableUiState.value = mutableUiState.value.transform()
    }

    private fun Double.normalize360(): Double = ((this % 360.0) + 360.0) % 360.0

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }

    companion object {
        private val ZERO_MOTOR_INTENSITIES = List(8) { 0 }
        val RUNNING_STAGES = setOf(SportStage.RunningCollapsed, SportStage.RunningExpanded)
        const val MAX_PROGRESS_ACCURACY_METERS = 25f
        const val MIN_ROUTE_MATCH_DISTANCE_METERS = 30.0
        const val BELT_GPS_STALE_AFTER_MS = 6_000L

        fun factory(context: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return SportViewModel(JsonRouteStore(context.applicationContext)) as T
                }
            }
        }
    }
}
