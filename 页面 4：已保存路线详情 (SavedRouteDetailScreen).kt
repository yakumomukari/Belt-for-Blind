@Composable
fun SavedRouteDetailScreen(
    routeId: String,
    navController: NavController,
    viewModel: SavedRouteDetailViewModel = viewModel()
) {
    val route by viewModel.route.collectAsState()
    val points by viewModel.points.collectAsState()

    LaunchedEffect(routeId) {
        viewModel.loadRoute(routeId)
    }

    route?.let { r ->
        Column(modifier = Modifier.fillMaxSize()) {
            // 轨迹地图占位
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "🗺️ ${r.name} 轨迹\n共 ${r.pointCount} 个点",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(r.name, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("点数: ${r.pointCount}")
                Text("保存时间: ${
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(Date(r.savedTime))
                }")
                Text("最近精度: ±${"%.1f".format(points.lastOrNull()?.accuracy ?: 0f)}m")

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("返回列表")
                }
            }
        }
    } ?: Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}