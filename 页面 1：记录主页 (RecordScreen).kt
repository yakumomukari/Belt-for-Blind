@Composable
fun RecordScreen(
    navController: NavController,
    viewModel: RecordViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pointCount by viewModel.pointCount.collectAsState()
    val accuracy by viewModel.latestAccuracy.collectAsState()
    val context = LocalContext.current

    // 权限请求示例 (真实项目需整合到 Effect 中)
    LaunchedEffect(Unit) {
        // 假设这里检查权限，暂时视为已授权
        viewModel.checkPermission(true)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 地图占位
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("🗺️ 地图区域", style = MaterialTheme.typography.headlineMedium)
        }

        // 权限拒绝蒙版
        if (uiState is RecordingUiState.PermissionDenied) {
            PermissionOverlay(
                onOpenSettings = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            )
        }

        // 底部信息与圆形按钮
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                    )
                )
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (uiState) {
                is RecordingUiState.Recording -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("记录中", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(8.dp))
                        RecordingDotsAnimation()
                    }
                    Text(
                        "点数: $pointCount  精度: ±${"%.1f".format(accuracy ?: 0f)}m",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is RecordingUiState.Idle -> Text(
                    "准备记录", color = Color.White, style = MaterialTheme.typography.titleLarge
                )
                is RecordingUiState.Stopped -> Text(
                    "记录已停止", color = Color.White, style = MaterialTheme.typography.titleLarge
                )
                else -> {}
            }

            Spacer(Modifier.height(16.dp))

            val isRecording = uiState is RecordingUiState.Recording
            FloatingActionButton(
                onClick = {
                    if (isRecording) {
                        viewModel.stopRecordingAndNavigate { pointsJson ->
                            navController.navigate("summary?points=$pointsJson")
                        }
                    } else {
                        viewModel.startRecording()
                    }
                },
                modifier = Modifier.size(84.dp),
                shape = CircleShape,
                containerColor = if (isRecording) Color(0xFFD32F2F) else Color(0xFF388E3C),
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(8.dp),
                enabled = uiState !is RecordingUiState.PermissionDenied
            ) {
                Icon(
                    painter = painterResource(
                        id = if (isRecording) R.drawable.ic_stop else R.drawable.ic_play
                    ),
                    contentDescription = if (isRecording) "停止记录" else "开始记录",
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}