package com.beltforblind.ui.record

sealed class RecordingUiState {
    data object Idle : RecordingUiState()
    data object Recording : RecordingUiState()
    data object Paused : RecordingUiState()
    data class Stopped(val pointCount: Int, val accuracy: Float?) : RecordingUiState()
    data object SaveSuccess : RecordingUiState()
    data object SaveFailure : RecordingUiState()
    data object PermissionDenied : RecordingUiState()
}
