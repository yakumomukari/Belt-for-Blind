// ========== RecordViewModel ==========
class RecordViewModel(
    private val recorder: LocationRecorder = MockLocationRecorder()
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecordingUiState>(RecordingUiState.Idle)
    val uiState: StateFlow<RecordingUiState> = _uiState

    private val _pointCount = MutableStateFlow(0)
    val pointCount: StateFlow<Int> = _pointCount

    private val _latestAccuracy = MutableStateFlow<Float?>(null)
    val latestAccuracy: StateFlow<Float?> = _latestAccuracy

    private var job: Job? = null

    fun startRecording() {
        recorder.startRecord()
        _uiState.value = RecordingUiState.Recording
        job = viewModelScope.launch {
            while (isActive) {
                _pointCount.value = recorder.getPointCount()
                _latestAccuracy.value = recorder.getLatestAccuracy()
                delay(1000)
            }
        }
    }

    fun stopRecordingAndNavigate(onNavigate: (String) -> Unit) {
        recorder.stopRecord()
        job?.cancel()
        val points = recorder.getRecordedPoints()
        val json = Uri.encode(Gson().toJson(points))
        _uiState.value = RecordingUiState.Stopped
        onNavigate(json)
    }

    fun checkPermission(hasPermission: Boolean) {
        _uiState.value = if (hasPermission) {
            if (_uiState.value is RecordingUiState.PermissionDenied) RecordingUiState.Idle
            else _uiState.value
        } else RecordingUiState.PermissionDenied
    }
}

// ========== RouteSummaryViewModel ==========
class RouteSummaryViewModel(
    private val recorder: LocationRecorder = MockLocationRecorder()
) : ViewModel() {

    private val _uiState = MutableStateFlow<SummaryUiState>(SummaryUiState.Idle)
    val uiState: StateFlow<SummaryUiState> = _uiState

    val snackbarHostState = SnackbarHostState()

    fun saveRoute(name: String) {
        viewModelScope.launch {
            _uiState.value = SummaryUiState.Saving
            val success = recorder.saveRoute(name)
            _uiState.value = if (success) SummaryUiState.SaveSuccess else SummaryUiState.SaveFailure
            snackbarHostState.showSnackbar(
                if (success) "路线已保存" else "保存失败，请重试"
            )
        }
    }
}

// ========== SavedRoutesViewModel ==========
class SavedRoutesViewModel(
    private val recorder: LocationRecorder = MockLocationRecorder()
) : ViewModel() {

    private val _routes = MutableStateFlow<List<Route>>(emptyList())
    val routes: StateFlow<List<Route>> = _routes

    fun loadRoutes() {
        _routes.value = recorder.loadRoutes()
    }
}

// ========== SavedRouteDetailViewModel ==========
class SavedRouteDetailViewModel(
    private val recorder: LocationRecorder = MockLocationRecorder()
) : ViewModel() {

    private val _route = MutableStateFlow<Route?>(null)
    val route: StateFlow<Route?> = _route

    private val _points = MutableStateFlow<List<LocationPoint>>(emptyList())
    val points: StateFlow<List<LocationPoint>> = _points

    fun loadRoute(id: String) {
        _route.value = recorder.getRouteById(id)
        _points.value = recorder.getPointsForRoute(id)
    }
}