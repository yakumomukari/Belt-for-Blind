class RecordViewModel(
    private val locationRecorder: LocationRecorder = MockLocationRecorder() // Agent A 接口
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecordingUiState>(RecordingUiState.Idle)
    val uiState: StateFlow<RecordingUiState> = _uiState

    private val _pointCount = MutableStateFlow(0)
    val pointCount: StateFlow<Int> = _pointCount

    private val _latestAccuracy = MutableStateFlow<Float?>(null)
    val latestAccuracy: StateFlow<Float?> = _latestAccuracy

    fun startRecording() {
        locationRecorder.startRecord()
        _uiState.value = RecordingUiState.Recording
        // 启动协程定期拉取点数与精度
        viewModelScope.launch {
            while (_uiState.value is RecordingUiState.Recording) {
                _pointCount.value = locationRecorder.getPointCount()
                _latestAccuracy.value = locationRecorder.getLatestAccuracy()
                delay(1000)
            }
        }
    }

    fun stopRecording() {
        locationRecorder.stopRecord()
        _uiState.value = RecordingUiState.Stopped(
            pointCount = locationRecorder.getPointCount(),
            accuracy = locationRecorder.getLatestAccuracy()
        )
    }

    fun saveRoute(name: String) {
        viewModelScope.launch {
            val success = locationRecorder.saveRoute(name)
            _uiState.value = if (success) {
                RecordingUiState.SaveSuccess
            } else {
                RecordingUiState.SaveFailure
            }
        }
    }
}