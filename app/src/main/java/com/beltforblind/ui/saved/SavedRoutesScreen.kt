package com.beltforblind.ui.saved

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beltforblind.route.model.RouteRecord
import com.beltforblind.ui.record.RouteDetailScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SavedRoutesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: SavedRoutesViewModel = viewModel(
        factory = SavedRoutesViewModel.factory(context.applicationContext),
    )
    val routes by viewModel.routes.collectAsState()
    var selectedRoute by remember { mutableStateOf<RouteRecord?>(null) }
    var routePendingDeletion by remember { mutableStateOf<RouteRecord?>(null) }
    var deletionFailedRouteName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadRoutes()
    }

    routePendingDeletion?.let { route ->
        AlertDialog(
            onDismissRequest = { routePendingDeletion = null },
            title = { Text("删除路线") },
            text = { Text("确定删除“${route.name}”吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        routePendingDeletion = null
                        if (!viewModel.deleteRoute(route.id)) {
                            deletionFailedRouteName = route.name
                        }
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { routePendingDeletion = null }) {
                    Text("取消")
                }
            },
        )
    }

    deletionFailedRouteName?.let { routeName ->
        AlertDialog(
            onDismissRequest = { deletionFailedRouteName = null },
            title = { Text("删除失败") },
            text = { Text("无法删除“$routeName”，请稍后重试。") },
            confirmButton = {
                TextButton(onClick = { deletionFailedRouteName = null }) {
                    Text("确定")
                }
            },
        )
    }

    selectedRoute?.let { route ->
        RouteDetailScreen(
            route = route,
            onBack = { selectedRoute = null },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("已保存路线", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { viewModel.loadRoutes() }) {
                Text("刷新")
            }
        }

        if (routes.isEmpty()) {
            EmptySavedRoutes()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(routes, key = { it.id }) { route ->
                    SavedRouteItem(
                        route = route,
                        onClick = { selectedRoute = route },
                        onLongClick = { routePendingDeletion = route },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySavedRoutes() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("暂无保存路线", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "完成记录并保存后，会在这里显示路线列表。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedRouteItem(
    route: RouteRecord,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(route.name, style = MaterialTheme.typography.titleMedium)
            Text("点数：${route.points.size}", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "保存时间：${route.createdAt.formatRouteTime()}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "点击查看详情，长按删除路线",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun Long.formatRouteTime(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(this))
}
