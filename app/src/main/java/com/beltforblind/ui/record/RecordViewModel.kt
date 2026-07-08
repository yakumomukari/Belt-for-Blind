package com.beltforblind.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.beltforblind.route.model.RouteRecord
import com.beltforblind.route.recorder.RouteRecorder
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

    private val _savedRoutes = MutableStateFlow<List<RouteRecord>>(emptyList())
    val savedRoutes: StateFlow<List<RouteRecord>> = _savedRoutes.asStateFlow()

    private var pollingJob: Job? = null

    fun startRecording() {
        routeRecorder.startRecord()
        _pointCount.value = 0
        _latestAccuracy.value = null
        _uiState.value = RecordingUiState.Recording

        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive && _uiState.value is RecordingUiState.Recording) {
                _pointCount.value = routeRecorder.getPointCount()
                _latestAccuracy.value = routeRecorder.getLatestAccuracy()
                delay(1000)
            }
        }
    }

    fun stopRecording() {
        routeRecorder.stopRecord()
        pollingJob?.cancel()

        val count = routeRecorder.getPointCount()
        val accuracy = routeRecorder.getLatestAccuracy()
        _pointCount.value = count
        _latestAccuracy.value = accuracy
        _uiState.value = RecordingUiState.Stopped(count, accuracy)
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

    fun requestPermissionAgain() {
        _uiState.value = RecordingUiState.Idle
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return RecordViewModel() as T
            }
        }
    }
}
