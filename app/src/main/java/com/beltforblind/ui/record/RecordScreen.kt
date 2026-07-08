package com.beltforblind.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun RecordScreen(
    viewModel: RecordViewModel = viewModel(factory = RecordViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    val pointCount by viewModel.pointCount.collectAsState()
    val accuracy by viewModel.latestAccuracy.collectAsState()
    val savedRoutes by viewModel.savedRoutes.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var routeName by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        when (uiState) {
            RecordingUiState.SaveSuccess -> snackbarHostState.showSnackbar("路线保存成功")
            RecordingUiState.SaveFailure -> snackbarHostState.showSnackbar("路线保存失败")
            RecordingUiState.PermissionDenied -> snackbarHostState.showSnackbar("定位权限未授权")
            else -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(state = uiState)

            if (uiState is RecordingUiState.PermissionDenied) {
                PermissionRequestCard(onRequestPermission = viewModel::requestPermissionAgain)
            }

            DataRow(label = "已记录点数", value = pointCount.toString())
            DataRow(label = "最近精度", value = accuracy?.let { "%.1f 米".format(it) } ?: "--")

            OutlinedTextField(
                value = routeName,
                onValueChange = { routeName = it },
                label = { Text("路线名称") },
                enabled = uiState is RecordingUiState.Stopped || uiState is RecordingUiState.SaveFailure,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { viewModel.startRecording() },
                    enabled = uiState is RecordingUiState.Idle ||
                        uiState is RecordingUiState.Stopped ||
                        uiState is RecordingUiState.SaveSuccess ||
                        uiState is RecordingUiState.SaveFailure,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("开始记录")
                }

                Button(
                    onClick = { viewModel.stopRecording() },
                    enabled = uiState is RecordingUiState.Recording,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("停止记录")
                }
            }

            Button(
                onClick = { viewModel.saveRoute(routeName) },
                enabled = (uiState is RecordingUiState.Stopped || uiState is RecordingUiState.SaveFailure) &&
                    routeName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存路线")
            }

            TextButton(
                onClick = { viewModel.loadRoutes() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("查看已保存路线")
            }

            if (savedRoutes.isNotEmpty()) {
                SavedRouteList(
                    routes = savedRoutes.map { "${it.name}：${it.points.size} 个点" },
                )
            }
        }
    }
}

@Composable
private fun StatusCard(state: RecordingUiState) {
    val (text, color) = when (state) {
        RecordingUiState.Idle -> "未开始" to MaterialTheme.colorScheme.outline
        RecordingUiState.Recording -> "记录中" to Color(0xFF2E7D32)
        is RecordingUiState.Stopped -> "已停止，等待保存" to MaterialTheme.colorScheme.primary
        RecordingUiState.SaveSuccess -> "保存成功" to Color(0xFF2E7D32)
        RecordingUiState.SaveFailure -> "保存失败" to MaterialTheme.colorScheme.error
        RecordingUiState.PermissionDenied -> "定位权限未授权" to Color(0xFFFFA000)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineMedium,
                color = color,
            )

            if (state is RecordingUiState.Recording) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "正在接收模拟定位点",
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                )
            }
        }
    }
}

@Composable
private fun PermissionRequestCard(onRequestPermission: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("需要定位权限后才能记录路线")
            Button(onClick = onRequestPermission) {
                Text("重新申请定位权限")
            }
        }
    }
}

@Composable
private fun DataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SavedRouteList(routes: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("已保存路线", style = MaterialTheme.typography.titleMedium)
        routes.forEach { route ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = route,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}
