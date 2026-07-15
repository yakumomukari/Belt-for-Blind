package com.beltforblind.ui.record

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.beltforblind.route.location.AMapLocationDataSource
import com.beltforblind.route.model.RoutePoint
import com.beltforblind.route.model.RouteRecord
import com.beltforblind.route.recorder.RouteRecordingManager
import com.beltforblind.route.recorder.RouteRecorder
import com.beltforblind.route.storage.JsonRouteStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RecordViewModel(
    private val routeRecorder: RouteRecorder = MockRouteRecorder(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<RecordingUiState>(RecordingUiState.Idle)
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    private val _pointCount = MutableStateFlow(0)
    val pointCount: StateFlow<Int> = _pointCount.asStateFlow()

    private val _latestAccuracy = MutableStateFlow<Float?>(null)
    val latestAccuracy: StateFlow<Float?> = _latestAccuracy.asStateFlow()

    private val _latestReceivedAccuracy = MutableStateFlow<Float?>(null)
    val latestReceivedAccuracy: StateFlow<Float?> = _latestReceivedAccuracy.asStateFlow()

    private val _latestPointAccepted = MutableStateFlow<Boolean?>(null)
    val latestPointAccepted: StateFlow<Boolean?> = _latestPointAccepted.asStateFlow()

    private val _discardedPointCount = MutableStateFlow(0)
    val discardedPointCount: StateFlow<Int> = _discardedPointCount.asStateFlow()

    private val _warmupRemainingSeconds = MutableStateFlow(0L)
    val warmupRemainingSeconds: StateFlow<Long> = _warmupRemainingSeconds.asStateFlow()

    private val _elapsedTimeSeconds = MutableStateFlow(0L)
    val elapsedTimeSeconds: StateFlow<Long> = _elapsedTimeSeconds.asStateFlow()

    private val _recentPoints = MutableStateFlow<List<RoutePoint>>(emptyList())
    val recentPoints: StateFlow<List<RoutePoint>> = _recentPoints.asStateFlow()

    private val _savedRoutes = MutableStateFlow<List<RouteRecord>>(emptyList())
    val savedRoutes: StateFlow<List<RouteRecord>> = _savedRoutes.asStateFlow()

    private var pollingJob: Job? = null
    private var activeSegmentStartedAt = 0L
    private var accumulatedRecordingDurationMs = 0L

    init {
        loadRoutes()
    }

    fun startRecording() {
        runCatching {
            routeRecorder.startRecord()
        }.onFailure {
            _uiState.value = RecordingUiState.PermissionDenied
            return
        }

        _pointCount.value = 0
        _latestAccuracy.value = null
        _latestReceivedAccuracy.value = null
        _latestPointAccepted.value = null
        _discardedPointCount.value = 0
        _warmupRemainingSeconds.value = 15L
        _elapsedTimeSeconds.value = 0L
        _recentPoints.value = emptyList()
        _uiState.value = RecordingUiState.Recording
        accumulatedRecordingDurationMs = 0L
        activeSegmentStartedAt = System.currentTimeMillis()
        startPolling()
    }

    fun pauseRecording() {
        if (_uiState.value !is RecordingUiState.Recording) return
        routeRecorder.pauseRecord()
        freezeActiveSegment()
        pollingJob?.cancel()
        refreshMetrics()
        _uiState.value = RecordingUiState.Paused
    }

    fun resumeRecording() {
        if (_uiState.value !is RecordingUiState.Paused) return
        runCatching {
            routeRecorder.resumeRecord()
        }.onFailure {
            _uiState.value = RecordingUiState.PermissionDenied
            return
        }
        activeSegmentStartedAt = System.currentTimeMillis()
        _uiState.value = RecordingUiState.Recording
        startPolling()
    }

    fun stopRecording() {
        routeRecorder.stopRecord()
        freezeActiveSegment()
        pollingJob?.cancel()
        refreshMetrics()
        _uiState.value = RecordingUiState.Stopped(_pointCount.value, _latestAccuracy.value)
    }

    fun saveRoute(name: String) {
        viewModelScope.launch {
            val saved = runCatching {
                routeRecorder.saveRoute(name.trim())
            }.isSuccess

            _uiState.value = if (saved) {
                _savedRoutes.value = routeRecorder.loadRoutes()
                RecordingUiState.SaveSuccess
            } else {
                RecordingUiState.SaveFailure
            }
        }
    }

    fun loadRoutes() {
        _savedRoutes.value = routeRecorder.loadRoutes()
    }

    fun onPermissionDenied() {
        _uiState.value = RecordingUiState.PermissionDenied
    }

    fun resetToIdle() {
        pollingJob?.cancel()
        _uiState.value = RecordingUiState.Idle
        _pointCount.value = 0
        _latestAccuracy.value = null
        _latestReceivedAccuracy.value = null
        _latestPointAccepted.value = null
        _discardedPointCount.value = 0
        _warmupRemainingSeconds.value = 0L
        _elapsedTimeSeconds.value = 0L
        _recentPoints.value = emptyList()
        activeSegmentStartedAt = 0L
        accumulatedRecordingDurationMs = 0L
    }

    private fun elapsedRecordingSeconds(): Long {
        val activeDuration = if (activeSegmentStartedAt == 0L) {
            0L
        } else {
            System.currentTimeMillis() - activeSegmentStartedAt
        }
        return ((accumulatedRecordingDurationMs + activeDuration) / 1_000L).coerceAtLeast(0L)
    }

    private fun freezeActiveSegment() {
        if (activeSegmentStartedAt == 0L) return
        accumulatedRecordingDurationMs += System.currentTimeMillis() - activeSegmentStartedAt
        activeSegmentStartedAt = 0L
        _elapsedTimeSeconds.value = elapsedRecordingSeconds()
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive && _uiState.value is RecordingUiState.Recording) {
                refreshMetrics()
                delay(1000)
            }
        }
    }

    private fun refreshMetrics() {
        _pointCount.value = routeRecorder.getPointCount()
        _latestAccuracy.value = routeRecorder.getLatestAccuracy()
        _latestReceivedAccuracy.value = routeRecorder.getLatestReceivedAccuracy()
        _latestPointAccepted.value = routeRecorder.isLatestPointAccepted()
        _discardedPointCount.value = routeRecorder.getDiscardedPointCount()
        _warmupRemainingSeconds.value = routeRecorder.getWarmupRemainingSeconds()
        _recentPoints.value = routeRecorder.getCurrentPoints()
        _elapsedTimeSeconds.value = elapsedRecordingSeconds()
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val recorder = RouteRecordingManager(
                        locationDataSource = AMapLocationDataSource(context.applicationContext),
                        routeStore = JsonRouteStore(context.applicationContext),
                    )
                    return RecordViewModel(recorder) as T
                }
            }
        }
    }
}
