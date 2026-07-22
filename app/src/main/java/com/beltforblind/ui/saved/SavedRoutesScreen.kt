package com.beltforblind.ui.saved

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beltforblind.route.model.RouteRecord
import com.beltforblind.ui.record.RouteDetailScreen
import com.beltforblind.ui.theme.BeltColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SavedRoutesScreen(
    onOpenRecordPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: SavedRoutesViewModel = viewModel(
        factory = SavedRoutesViewModel.factory(context.applicationContext),
    )
    val routes by viewModel.routes.collectAsState()
    val thumbnailCache = remember(context) {
        RouteThumbnailCache(context.applicationContext)
    }
    val listState = rememberLazyListState()
    var thumbnailVersion by remember { mutableIntStateOf(0) }
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
                        if (viewModel.deleteRoute(route.id)) {
                            thumbnailCache.delete(route)
                            thumbnailVersion += 1
                        } else {
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BeltColors.Background)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            SavedRoutesHeader(onRefresh = viewModel::loadRoutes)
            if (routes.isEmpty()) {
                EmptySavedRoutes(onOpenRecordPage = onOpenRecordPage)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(routes, key = RouteRecord::id) { route ->
                        SavedRouteItem(
                            route = route,
                            thumbnailCache = thumbnailCache,
                            thumbnailVersion = thumbnailVersion,
                            onClick = { selectedRoute = route },
                            onLongClick = { routePendingDeletion = route },
                        )
                    }
                }
            }
        }
        RouteThumbnailRendererHost(
            routes = routes,
            cache = thumbnailCache,
            onThumbnailGenerated = { thumbnailVersion += 1 },
        )
    }
}

@Composable
private fun SavedRoutesHeader(onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "已保存路线",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "查看、管理你保存的路线",
                style = MaterialTheme.typography.bodySmall,
                color = BeltColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.size(8.dp))
        FilledTonalButton(
            onClick = onRefresh,
            shape = CircleShape,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = BeltColors.PurpleContainer,
                contentColor = BeltColors.PrimaryPurple,
            ),
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("刷新")
        }
    }
}

@Composable
private fun EmptySavedRoutes(onOpenRecordPage: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = BeltColors.PurpleContainer,
                contentColor = BeltColors.PrimaryPurple,
            ) {
                Icon(
                    Icons.Rounded.Bookmark,
                    contentDescription = null,
                    modifier = Modifier.padding(14.dp).size(28.dp),
                )
            }
            Text("还没有保存路线", style = MaterialTheme.typography.titleMedium)
            Text(
                "先去记录一条路线",
                style = MaterialTheme.typography.bodySmall,
                color = BeltColors.TextSecondary,
            )
            Button(
                onClick = onOpenRecordPage,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BeltColors.PrimaryPurple),
            ) {
                Text("前往记录")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedRouteItem(
    route: RouteRecord,
    thumbnailCache: RouteThumbnailCache,
    thumbnailVersion: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics {
                contentDescription = "${route.name}，${route.points.size}个路线点，保存于${route.createdAt.formatRouteTime()}，点击查看详情，长按删除"
            },
        shape = RoundedCornerShape(20.dp),
        color = BeltColors.Surface,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RouteThumbnail(
                route = route,
                cache = thumbnailCache,
                cacheVersion = thumbnailVersion,
                modifier = Modifier.size(108.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    route.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SavedRouteInfoLine(
                    icon = Icons.Rounded.Route,
                    text = "路线点数 ${route.points.size}",
                )
                SavedRouteInfoLine(
                    icon = Icons.Rounded.Schedule,
                    text = route.createdAt.formatRouteTime(),
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = BeltColors.PrimaryPurple,
            )
        }
    }
}

@Composable
private fun SavedRouteInfoLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = BeltColors.PrimaryPurple, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = BeltColors.TextSecondary)
    }
}

@Composable
private fun RouteThumbnail(
    route: RouteRecord,
    cache: RouteThumbnailCache,
    cacheVersion: Int,
    modifier: Modifier = Modifier,
) {
    var thumbnail by remember(route, cacheVersion) {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(route, cacheVersion) {
        thumbnail = withContext(Dispatchers.IO) {
            cache.load(route)?.asImageBitmap()
        }
    }

    Surface(
        modifier = modifier.semantics {
            contentDescription = "${route.name}地图路线缩略图"
        },
        shape = RoundedCornerShape(16.dp),
        color = BeltColors.PurpleContainer,
    ) {
        Crossfade(
            targetState = thumbnail,
            animationSpec = tween(240),
        ) { bitmap ->
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                RouteShapeFallback(
                    route = route,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun RouteShapeFallback(
    route: RouteRecord,
    modifier: Modifier = Modifier,
) {
    val points = remember(route.points) { route.points }
    Canvas(
        modifier = modifier
            .background(BeltColors.PurpleContainer, RoundedCornerShape(16.dp)),
    ) {
        if (points.isEmpty()) return@Canvas
        val minLatitude = points.minOf { it.latitude }
        val maxLatitude = points.maxOf { it.latitude }
        val minLongitude = points.minOf { it.longitude }
        val maxLongitude = points.maxOf { it.longitude }
        val latitudeSpan = (maxLatitude - minLatitude).takeIf { it > 0.0 } ?: 1.0
        val longitudeSpan = (maxLongitude - minLongitude).takeIf { it > 0.0 } ?: 1.0
        val inset = size.minDimension * 0.14f
        val width = size.width - inset * 2f
        val height = size.height - inset * 2f
        fun position(index: Int): Offset {
            val point = points[index]
            return Offset(
                x = inset + ((point.longitude - minLongitude) / longitudeSpan).toFloat() * width,
                y = inset + (1f - ((point.latitude - minLatitude) / latitudeSpan).toFloat()) * height,
            )
        }
        val path = Path().apply {
            val first = position(0)
            moveTo(first.x, first.y)
            for (index in 1..points.lastIndex) {
                val next = position(index)
                lineTo(next.x, next.y)
            }
        }
        drawPath(
            path = path,
            color = BeltColors.PrimaryPurple,
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        val start = position(0)
        val end = position(points.lastIndex)
        drawCircle(BeltColors.Surface, radius = 7.dp.toPx(), center = start)
        drawCircle(BeltColors.PrimaryPurple, radius = 4.dp.toPx(), center = start)
        drawCircle(BeltColors.Surface, radius = 7.dp.toPx(), center = end)
        drawCircle(BeltColors.PrimaryPurpleDark, radius = 4.dp.toPx(), center = end)
    }
}

private fun Long.formatRouteTime(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(this))
}
