package com.beltforblind.ui.sport

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.beltforblind.route.model.RoutePoint
import com.beltforblind.route.model.RouteRecord
import com.beltforblind.ui.theme.BeltColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
internal fun RoutePickerScreen(
    routes: List<RouteRecord>,
    selectedRouteId: String?,
    loading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelectRoute: (RouteRecord) -> Unit,
    onOpenRecordPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.semantics { contentDescription = "返回运动首页" },
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
            }
            Text(
                text = "选择路线",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onRetry,
                enabled = !loading,
                modifier = Modifier.semantics { contentDescription = "刷新路线列表" },
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
            }
        }
        when {
            loading && routes.isEmpty() -> RouteLoadingState()
            errorMessage != null && routes.isEmpty() -> RouteLoadErrorState(errorMessage, onRetry)
            routes.isEmpty() -> EmptyRouteState(onOpenRecordPage)
            else -> RouteList(routes, selectedRouteId, loading, errorMessage, onSelectRoute)
        }
    }
}

@Composable
private fun RouteLoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.semantics { contentDescription = "正在读取已保存路线" },
        )
    }
}

@Composable
private fun RouteLoadErrorState(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(message, style = MaterialTheme.typography.titleMedium)
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun EmptyRouteState(onOpenRecordPage: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Route,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Text("暂无保存路线", style = MaterialTheme.typography.titleLarge)
            Text("先完成一次路线记录并保存，再回来选择。")
            Button(onClick = onOpenRecordPage) { Text("去记录路线") }
        }
    }
}

@Composable
private fun RouteList(
    routes: List<RouteRecord>,
    selectedRouteId: String?,
    loading: Boolean,
    errorMessage: String?,
    onSelectRoute: (RouteRecord) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (loading) {
            item {
                Text("正在刷新路线…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (errorMessage != null) {
            item {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }
        }
        items(routes, key = RouteRecord::id) { route ->
            RoutePickerItem(
                route = route,
                selected = route.id == selectedRouteId,
                onClick = { onSelectRoute(route) },
            )
        }
    }
}

@Composable
private fun RoutePickerItem(route: RouteRecord, selected: Boolean, onClick: () -> Unit) {
    val distanceText = route.totalDistanceMeters().formatDistance()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "路线 ${route.name}，距离 $distanceText" }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) BeltColors.SportGreen.copy(alpha = 0.14f)
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    route.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                    Text("已选择", color = BeltColors.SportGreenDark)
                }
            }
            Text(distanceText, style = MaterialTheme.typography.headlineSmall)
            Text("创建时间：${route.createdAt.formatRouteTime()}")
        }
    }
}

internal fun RouteRecord.totalDistanceMeters(): Double {
    return points.zipWithNext().sumOf { (start, end) -> start.distanceTo(end) }
}

private fun RoutePoint.distanceTo(other: RoutePoint): Double {
    val startLatitude = Math.toRadians(latitude)
    val endLatitude = Math.toRadians(other.latitude)
    val latitudeDelta = endLatitude - startLatitude
    val longitudeDelta = Math.toRadians(other.longitude - longitude)
    val haversine = sin(latitudeDelta / 2).let { it * it } +
        cos(startLatitude) * cos(endLatitude) *
        sin(longitudeDelta / 2).let { it * it }
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
}

private fun Double.formatDistance(): String {
    return if (this < 1_000.0) {
        "%.0f 米".format(Locale.getDefault(), this)
    } else {
        "%.2f 公里".format(Locale.getDefault(), this / 1_000.0)
    }
}

private fun Long.formatRouteTime(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(this))
}

private const val EARTH_RADIUS_METERS = 6_371_000.0
