sealed class RecordingUiState {
    object Idle : RecordingUiState()                          // 未开始
    object Recording : RecordingUiState()                     // 记录中
    data class Stopped(val pointCount: Int, val accuracy: Float?) : RecordingUiState()  // 已停止，等待保存
    object SaveSuccess : RecordingUiState()                   // 保存成功
    object SaveFailure : RecordingUiState()                   // 保存失败
    object PermissionDenied : RecordingUiState()              // 定位权限未授权
}