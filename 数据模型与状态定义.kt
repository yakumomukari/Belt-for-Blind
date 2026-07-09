// ========== 数据模型 ==========
data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long
)

data class Route(
    val id: String,
    val name: String,
    val pointCount: Int,
    val savedTime: Long
)

// ========== 页面状态 ==========
sealed class RecordingUiState {
    object Idle : RecordingUiState()
    object Recording : RecordingUiState()
    object Stopped : RecordingUiState()
    object PermissionDenied : RecordingUiState()
}

sealed class SummaryUiState {
    object Idle : SummaryUiState()
    object Saving : SummaryUiState()
    object SaveSuccess : SummaryUiState()
    object SaveFailure : SummaryUiState()
}