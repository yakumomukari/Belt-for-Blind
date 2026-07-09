@Composable
fun RouteSummaryScreen(
    pointsJson: String,
    navController: NavController,
    viewModel: RouteSummaryViewModel = viewModel()
) {
    val points: List<LocationPoint> = remember(pointsJson) {
        val type = object : TypeToken<List<LocationPoint>>() {}.type
        Gson().fromJson(pointsJson, type)
    }
    val uiState by viewModel.uiState.collectAsState()
    var routeName by remember { mutableStateOf("") }

    Scaffold(
        snackbarHost = { SnackbarHost(viewModel.snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 地图轨迹占位
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f),
                contentAlignment = Alignment.Center
            ) {
                Text("🗺️ 轨迹预览\n共 ${points.size} 个点", style = MaterialTheme.typography.titleMedium)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // 状态文字
                val statusText = when (uiState) {
                    is SummaryUiState.Idle -> "已停止，等待保存"
                    is SummaryUiState.Saving -> "正在保存..."
                    is SummaryUiState.SaveSuccess -> "保存成功 ✅"
                    is SummaryUiState.SaveFailure -> "保存失败 ❌"
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleLarge,
                    color = when (uiState) {
                        is SummaryUiState.SaveSuccess -> Color(0xFF2E7D32)
                        is SummaryUiState.SaveFailure -> Color(0xFFB00020)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text("点数: ${points.size}")
                    Text("精度: ±${"%.1f".format(points.lastOrNull()?.accuracy ?: 0f)}m")
                }

                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = routeName,
                    onValueChange = { routeName = it },
                    label = { Text("路线名称") },
                    enabled = uiState !is SummaryUiState.Saving,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.saveRoute(routeName) },
                    enabled = routeName.isNotBlank() && uiState !is SummaryUiState.Saving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存路线")
                }

                Spacer(Modifier.height(12.dp))

                TextButton(
                    onClick = {
                        navController.popBackStack("home", false)
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("返回主页")
                }
            }
        }
    }
}