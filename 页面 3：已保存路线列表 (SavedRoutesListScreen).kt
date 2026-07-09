@Composable
fun SavedRoutesListScreen(
    navController: NavController,
    viewModel: SavedRoutesViewModel = viewModel()
) {
    val routes by viewModel.routes.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadRoutes()
    }

    if (routes.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Spacer(Modifier.height(16.dp))
                Text("暂无保存路线", style = MaterialTheme.typography.titleMedium)
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(routes, key = { it.id }) { route ->
                RouteListItem(route = route) {
                    navController.navigate("route_detail?routeId=${route.id}")
                }
            }
        }
    }
}

@Composable
fun RouteListItem(route: Route, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(route.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(Date(route.savedTime)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text("点数: ${route.pointCount}", style = MaterialTheme.typography.bodySmall)
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "查看详情",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}