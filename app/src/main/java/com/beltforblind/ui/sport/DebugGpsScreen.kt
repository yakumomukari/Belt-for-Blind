package com.beltforblind.ui.sport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.beltforblind.route.location.LocationSimulationProvider
import com.beltforblind.route.location.SimulationScenario

@Composable
fun DebugGpsScreen(
    onOpenRecordPage: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val simulationState by LocationSimulationProvider.state.collectAsState()
    var selectedScenario by remember { mutableStateOf(simulationState.scenario) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("虚拟 GPS 测试", style = MaterialTheme.typography.headlineMedium)
        Text("仅 Debug 构建可用。虚拟点每 3 秒发送一次，记录页仍会执行 15 秒预热和 8 米精度过滤。")

        Text("测试路线", style = MaterialTheme.typography.titleMedium)
        SimulationScenario.entries.forEach { scenario ->
            FilterChip(
                selected = selectedScenario == scenario,
                onClick = { selectedScenario = scenario },
                label = { Text(scenario.displayName()) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(selectedScenario.description(), style = MaterialTheme.typography.bodyMedium)

        Text(
            text = if (simulationState.enabled) "状态：虚拟 GPS 运行中" else "状态：未启动",
            style = MaterialTheme.typography.titleMedium,
        )
        Text("已发送点数：${simulationState.emittedPointCount}")
        simulationState.currentPoint?.let { point ->
            Text("纬度：%.6f".format(point.latitude))
            Text("经度：%.6f".format(point.longitude))
            Text("精度：${point.accuracy?.let { "%.1f 米".format(it) } ?: "--"}")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { LocationSimulationProvider.start(selectedScenario) },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (simulationState.enabled) "重新开始" else "启动")
            }
            OutlinedButton(
                onClick = LocationSimulationProvider::stop,
                enabled = simulationState.enabled,
                modifier = Modifier.weight(1f),
            ) {
                Text("停止")
            }
        }

        Button(
            onClick = onOpenRecordPage,
            enabled = simulationState.enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("进入记录页测试")
        }
        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("返回运动页")
        }
    }
}

private fun SimulationScenario.displayName(): String {
    return when (this) {
        SimulationScenario.Straight -> "直线路线"
        SimulationScenario.RightAngleTurn -> "直角转弯路线"
        SimulationScenario.AccuracyFilter -> "精度过滤路线"
    }
}

private fun SimulationScenario.description(): String {
    return when (this) {
        SimulationScenario.Straight -> "持续向东移动，每个点精度为 3 米。"
        SimulationScenario.RightAngleTurn -> "先向北再向东移动，用于检查轨迹和切线方向。"
        SimulationScenario.AccuracyFilter -> "交替发送 3 米和 15 米精度点，用于检查不合格点是否被丢弃。"
    }
}
