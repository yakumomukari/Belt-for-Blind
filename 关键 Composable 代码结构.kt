@Composable
fun RecordScreen(viewModel: RecordViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val pointCount by viewModel.pointCount.collectAsState()
    val accuracy by viewModel.latestAccuracy.collectAsState()
    var routeName by remember { mutableStateOf("") }

    Scaffold(
        snackbarHost = { /* SnackbarHost */ }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 状态卡片
            StatusCard(state = state)

            // 权限拒绝时的特殊提示
            if (state is RecordingUiState.PermissionDenied) {
                PermissionRequestCard(onOpenSettings = { /* 打开应用设置 */ })
            }

            // 数据展示
            DataRow(label = "已记录点数", value = pointCount.toString())
            DataRow(label = "最近精度", value = accuracy?.let { "±%.1f 米".format(it) } ?: "--")

            // 路线名称输入（仅 Stopped / SaveFailure 时可编辑）
            OutlinedTextField(
                value = routeName,
                onValueChange = { routeName = it },
                label = { Text("路线名称") },
                enabled = state is RecordingUiState.Stopped || state is RecordingUiState.SaveFailure,
                modifier = Modifier.fillMaxWidth()
            )

            // 操作按钮行
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { viewModel.startRecording() },
                    enabled = state is RecordingUiState.Idle || state is RecordingUiState.Stopped
                            || state is RecordingUiState.SaveSuccess || state is RecordingUiState.SaveFailure
                ) { Text("开始记录") }

                Button(
                    onClick = { viewModel.stopRecording() },
                    enabled = state is RecordingUiState.Recording
                ) { Text("停止记录") }
            }

            Button(
                onClick = { viewModel.saveRoute(routeName) },
                enabled = (state is RecordingUiState.Stopped || state is RecordingUiState.SaveFailure) && routeName.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存路线") }

            // 加载历史路线（可折叠）
            TextButton(onClick = { /* 弹窗显示路线列表 */ }) {
                Text("查看已保存路线")
            }
        }
    }
}

@Composable
fun StatusCard(state: RecordingUiState) {
    val (text, color) = when (state) {
        is RecordingUiState.Idle -> "未开始" to MaterialTheme.colorScheme.outline
        is RecordingUiState.Recording -> "记录中" to Color(0xFF4CAF50)
        is RecordingUiState.Stopped -> "已停止" to MaterialTheme.colorScheme.primary
        is RecordingUiState.SaveSuccess -> "保存成功" to Color(0xFF2E7D32)
        is RecordingUiState.SaveFailure -> "保存失败" to Color(0xFFB00020)
        is RecordingUiState.PermissionDenied -> "定位权限未授权" to Color(0xFFFFA000)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp),
            color = color
        )
        // 如果记录中，显示动画点
        if (state is RecordingUiState.Recording) {
            RecordingDotsAnimation()
        }
    }
}