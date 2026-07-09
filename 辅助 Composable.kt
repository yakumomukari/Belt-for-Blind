@Composable
fun PermissionOverlay(onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.LocationOff,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(16.dp))
                Text("定位权限未授权", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("请前往设置授予定位权限", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onOpenSettings) {
                    Text("前往设置")
                }
            }
        }
    }
}

@Composable
fun RecordingDotsAnimation() {
    val infiniteTransition = rememberInfiniteTransition()
    val dots = listOf(0, 200, 400).map { delay ->
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, delayMillis = delay),
                repeatMode = RepeatMode.Reverse
            )
        )
    }
    Row {
        dots.forEach { alpha ->
            Text(".", color = Color.White.copy(alpha = alpha.value), fontSize = 24.sp)
        }
    }
}