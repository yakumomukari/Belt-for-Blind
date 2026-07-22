package com.beltforblind.ui.sport

import com.beltforblind.route.model.RoutePoint
import com.beltforblind.route.model.RouteRecord
import com.beltforblind.route.tangent.RouteTangent
import com.beltforblind.navigation.vibration.NavigationVibrationDecision

enum class SportStage {
    Preparing,
    RunningCollapsed,
    RunningExpanded,
    Paused,
    Finished,
}

enum class GpsQuality(val label: String) {
    Good("良好"),
    Fair("一般"),
    Poor("较差"),
    Unavailable("不可用"),
}

data class GpsState(
    val quality: GpsQuality = GpsQuality.Unavailable,
    val accuracyMeters: Float? = null,
    val errorMessage: String? = null,
) {
    companion object {
        fun from(point: RoutePoint): GpsState {
            val accuracy = point.accuracy
            val quality = when {
                accuracy == null -> GpsQuality.Unavailable
                accuracy <= GPS_GOOD_ACCURACY_METERS -> GpsQuality.Good
                accuracy <= GPS_FAIR_ACCURACY_METERS -> GpsQuality.Fair
                else -> GpsQuality.Poor
            }
            return GpsState(quality = quality, accuracyMeters = accuracy)
        }
    }
}

enum class BeltConnectionState(val label: String) {
    Disconnected("腰带未连接"),
    Connecting("腰带连接中"),
    Connected("腰带已连接"),
    Error("腰带连接异常"),
}

enum class LocationSourceKind {
    Phone,
    Belt,
}

data class SportUiState(
    val stage: SportStage = SportStage.Preparing,
    val gpsState: GpsState = GpsState(),
    val selectedRoute: RouteRecord? = null,
    val availableRoutes: List<RouteRecord> = emptyList(),
    val routesLoading: Boolean = false,
    val routeLoadError: String? = null,
    val currentLocation: RoutePoint? = null,
    val locationSource: LocationSourceKind = LocationSourceKind.Phone,
    val routeProgress: Float = 0f,
    val routeTangent: RouteTangent? = null,
    val headingDegrees: Double? = null,
    val navigationVibrationDecision: NavigationVibrationDecision = NavigationVibrationDecision(),
    val elapsedTimeSeconds: Long = 0L,
    val distanceMeters: Double = 0.0,
    val paceSecondsPerKilometer: Long? = null,
    val calories: Int? = null,
    val heartRate: Int? = null,
    val beltConnectionState: BeltConnectionState = BeltConnectionState.Disconnected,
    val locationPermissionGranted: Boolean = false,
    val isGpsStatusVisible: Boolean = false,
    val isRoutePickerRequested: Boolean = false,
    val isMapFollowingUser: Boolean = true,
    val recenterRequestId: Long = 0L,
    val isHoldingToStop: Boolean = false,
    val message: String? = null,
) {
    val canStart: Boolean
        get() = selectedRoute?.points?.size?.let { it >= MIN_ROUTE_POINT_COUNT } == true &&
            (locationPermissionGranted || locationSource == LocationSourceKind.Belt) &&
            currentLocation != null
}

sealed interface SportUiEvent {
    data object OpenGpsStatus : SportUiEvent
    data object DismissGpsStatus : SportUiEvent
    data object OpenRoutePicker : SportUiEvent
    data object ReloadRoutes : SportUiEvent
    data class SelectRoute(val route: RouteRecord) : SportUiEvent
    data object DismissRoutePicker : SportUiEvent
    data object StartRunning : SportUiEvent
    data object ExpandMetrics : SportUiEvent
    data object CollapseMetrics : SportUiEvent
    data object PauseRunning : SportUiEvent
    data object ResumeRunning : SportUiEvent
    data object BeginHoldToStop : SportUiEvent
    data object CancelHoldToStop : SportUiEvent
    data object ConfirmStop : SportUiEvent
    data object ReturnToPreparing : SportUiEvent
    data object RecenterMap : SportUiEvent
    data object MapMovedByUser : SportUiEvent
    data class LocationPermissionChanged(val granted: Boolean) : SportUiEvent
    data class LocationUpdated(val point: RoutePoint) : SportUiEvent
    data class BeltLocationUpdated(val point: RoutePoint) : SportUiEvent
    data class LocationFailed(val code: Int, val message: String) : SportUiEvent
    data class HeadingUpdated(val headingDegrees: Double) : SportUiEvent
    data object HeadingUnavailable : SportUiEvent
    data class BeltConnectionChanged(val state: BeltConnectionState) : SportUiEvent
    data class BackgroundLockFailed(val message: String) : SportUiEvent
    data object DismissMessage : SportUiEvent
}

const val GPS_GOOD_ACCURACY_METERS = 10f
const val GPS_FAIR_ACCURACY_METERS = 25f
const val MIN_ROUTE_POINT_COUNT = 2
