package com.beltforblind.ui.sport

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.beltforblind.motor.BleMotorController
import com.beltforblind.motor.MotorConnectionStatus
import com.beltforblind.motor.MotorControlGateway
import com.beltforblind.navigation.heading.AndroidPhoneHeadingProvider
import com.beltforblind.navigation.heading.PhoneHeadingAccuracy
import com.beltforblind.navigation.heading.PhoneHeadingSample
import com.beltforblind.navigation.heading.PhoneHeadingStatus
import com.beltforblind.navigation.vibration.NavigationVibrationPlanner
import com.beltforblind.route.location.LocationSimulationProvider
import com.beltforblind.route.location.SimulationScenario
import com.beltforblind.route.tangent.RouteTangent
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DebugGpsScreen(
    onOpenRecordPage: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val simulationState by LocationSimulationProvider.state.collectAsState()
    var selectedScenario by remember { mutableStateOf(simulationState.scenario) }
    val context = LocalContext.current
    val motorController = remember(context) { BleMotorController(context.applicationContext) }

    DisposableEffect(motorController) {
        onDispose { motorController.close() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Debug 测试工具", style = MaterialTheme.typography.headlineMedium)

        Text("虚拟 GPS", style = MaterialTheme.typography.titleLarge)
        Text("虚拟点每 3 秒发送一次，记录页仍会执行 15 秒预热和 8 米精度过滤。")

        Text("测试路线", style = MaterialTheme.typography.titleMedium)
        SimulationScenario.entries.forEach { scenario ->
            FilterChip(
                selected = selectedScenario == scenario,
                onClick = { selectedScenario = scenario },
                label = { Text(scenario.displayName()) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(selectedScenario.description(), style = MaterialTheme.typography.bodyMedium)

        Text(
            text = if (simulationState.enabled) "状态：虚拟 GPS 运行中" else "状态：未启动",
            style = MaterialTheme.typography.titleMedium,
        )
        Text("已发送点数：${simulationState.emittedPointCount}")
        simulationState.currentPoint?.let { point ->
            Text("纬度：%.6f".format(point.latitude))
            Text("经度：%.6f".format(point.longitude))
            Text("精度：${point.accuracy?.let { "%.1f 米".format(it) } ?: "--"}")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { LocationSimulationProvider.start(selectedScenario) },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (simulationState.enabled) "重新开始" else "启动")
            }
            OutlinedButton(
                onClick = LocationSimulationProvider::stop,
                enabled = simulationState.enabled,
                modifier = Modifier.weight(1f),
            ) {
                Text("停止")
            }
        }

        Button(
            onClick = onOpenRecordPage,
            enabled = simulationState.enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("进入记录页测试")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        MotorDebugPanel(controller = motorController)

        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("返回运动页")
        }
    }
}

@Composable
private fun MotorDebugPanel(controller: MotorControlGateway) {
    val context = LocalContext.current
    val state by controller.state.collectAsState()
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var northTestEnabled by remember { mutableStateOf(false) }
    var headingSample by remember { mutableStateOf(PhoneHeadingSample()) }
    var lastAutomaticMotor by remember { mutableStateOf<Int?>(null) }
    val headingProvider = remember(context) {
        AndroidPhoneHeadingProvider(context.applicationContext) { sample ->
            headingSample = sample
        }
    }
    val northDecision = headingSample.headingDegrees?.let { heading ->
        NavigationVibrationPlanner.plan(
            tangent = NORTH_ROUTE_TANGENT,
            headingDegrees = heading,
            locationAccuracyMeters = NORTH_TEST_ACCURACY_METERS,
        )
    }
    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (requiredPermissions.all { result[it] == true }) {
            permissionMessage = null
            controller.scanAndConnect()
        } else {
            permissionMessage = "蓝牙权限被拒绝，无法扫描腰带"
        }
    }
    val requestConnection = {
        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            permissionMessage = null
            controller.scanAndConnect()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    DisposableEffect(headingProvider, northTestEnabled) {
        if (northTestEnabled) headingProvider.start()
        onDispose {
            if (northTestEnabled) headingProvider.stop()
        }
    }

    LaunchedEffect(state.isConnected) {
        if (!state.isConnected && northTestEnabled) {
            northTestEnabled = false
            lastAutomaticMotor = null
        }
    }

    LaunchedEffect(
        northTestEnabled,
        state.isConnected,
        headingSample.status,
        northDecision?.motorNumber,
    ) {
        if (!northTestEnabled || !state.isConnected) {
            lastAutomaticMotor = null
            return@LaunchedEffect
        }

        val targetMotor = northDecision?.motorNumber
        if (targetMotor == lastAutomaticMotor) return@LaunchedEffect
        if (targetMotor == null) {
            controller.stopMotor()
        } else {
            controller.activateMotor(targetMotor)
        }
        lastAutomaticMotor = targetMotor
    }

    Text("腰带电机测试", style = MaterialTheme.typography.titleLarge)
    Text("连接后点击轮盘外圈选择单个电机，点击中心 STOP 停止全部电机。")
    Text(
        text = "连接状态：${state.status.displayName()}",
        style = MaterialTheme.typography.titleMedium,
    )
    state.deviceName?.let { Text("设备：$it") }
    (permissionMessage ?: state.message)?.let { Text(it) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = requestConnection,
            enabled = state.status !in setOf(
                MotorConnectionStatus.Scanning,
                MotorConnectionStatus.Connecting,
                MotorConnectionStatus.Connected,
            ),
            modifier = Modifier.weight(1f),
        ) {
            Text("扫描并连接")
        }
        OutlinedButton(
            onClick = controller::disconnect,
            enabled = state.status != MotorConnectionStatus.Disconnected,
            modifier = Modifier.weight(1f),
        ) {
            Text("断开")
        }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    Text("正北路线自动测试", style = MaterialTheme.typography.titleLarge)
    Text("固定路线方向为北方。手机需竖直放在人体正前方，屏幕朝向身体、背面朝外。")
    Text(
        text = "朝向状态：${headingSample.status.displayName()}",
        style = MaterialTheme.typography.titleMedium,
    )
    headingSample.headingDegrees?.let { heading ->
        Text("人体朝向：%.1f°（磁北基准）".format(heading))
        Text("传感器精度：${headingSample.accuracy.displayName()}")
    }
    Text(
        text = northDecision?.motorNumber?.let { motor ->
            "正北引导建议：$motor 号电机"
        } ?: "正北引导建议：停止",
        style = MaterialTheme.typography.titleMedium,
    )
    Text("预期：面北→1号，面东→7号，面南→5号，面西→3号。")
    Button(
        onClick = {
            if (northTestEnabled) {
                northTestEnabled = false
                lastAutomaticMotor = null
                controller.stopMotor()
            } else {
                controller.stopMotor()
                lastAutomaticMotor = null
                northTestEnabled = true
            }
        },
        enabled = state.isConnected,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (northTestEnabled) "停止正北自动测试" else "启动正北自动测试")
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    Text("手动轮盘", style = MaterialTheme.typography.titleLarge)
    MotorWheel(
        selectedMotor = state.selectedMotor,
        enabled = state.isConnected && !northTestEnabled,
        onMotorSelected = controller::activateMotor,
        onStop = controller::stopMotor,
    )
    Text(
        text = state.selectedMotor?.let { "当前电机：$it" } ?: "当前电机：全部停止",
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = "调试时请确保电机由驱动电路和独立电源供电，不要直接由 ESP32 GPIO 驱动。",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun MotorWheel(
    selectedMotor: Int?,
    enabled: Boolean,
    onMotorSelected: (Int) -> Unit,
    onStop: () -> Unit,
) {
    val selectedColor = MaterialTheme.colorScheme.primary
    val normalColor = MaterialTheme.colorScheme.surfaceVariant
    val disabledColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val dividerColor = MaterialTheme.colorScheme.outline
    val selectedTextColor = MaterialTheme.colorScheme.onPrimary
    val normalTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val stopColor = if (enabled) MaterialTheme.colorScheme.error else disabledColor
    val stopTextColor = if (enabled) MaterialTheme.colorScheme.onError else normalTextColor

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(12.dp)
            .semantics {
                contentDescription = if (enabled) {
                    "电机控制轮盘，当前${selectedMotor?.let { "$it 号电机" } ?: "全部停止"}"
                } else {
                    "电机控制轮盘未启用"
                }
            }
            .pointerInput(enabled) {
                detectTapGestures { position ->
                    if (!enabled) return@detectTapGestures
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val delta = position - center
                    val distance = delta.getDistance()
                    val radius = minOf(size.width, size.height) / 2f
                    when {
                        distance <= radius * STOP_RADIUS_RATIO -> onStop()
                        distance <= radius -> {
                            val angle = Math.toDegrees(atan2(delta.y, delta.x).toDouble())
                            val clockwiseFromTop = (angle + 90.0 + 360.0) % 360.0
                            val motor = ((clockwiseFromTop + 22.5) / 45.0).toInt() % 8 + 1
                            onMotorSelected(motor)
                        }
                    }
                }
            },
    ) {
        val radius = size.minDimension / 2f
        val topLeft = Offset(center.x - radius, center.y - radius)
        val wheelSize = Size(radius * 2f, radius * 2f)
        val numberPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 22.sp.toPx()
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        repeat(8) { index ->
            val motorNumber = index + 1
            drawArc(
                color = when {
                    !enabled -> disabledColor
                    selectedMotor == motorNumber -> selectedColor
                    else -> normalColor
                },
                startAngle = -112.5f + index * 45f,
                sweepAngle = 45f,
                useCenter = true,
                topLeft = topLeft,
                size = wheelSize,
            )
            drawArc(
                color = dividerColor,
                startAngle = -112.5f + index * 45f,
                sweepAngle = 45f,
                useCenter = true,
                topLeft = topLeft,
                size = wheelSize,
                style = Stroke(width = 1.dp.toPx()),
            )

            val labelAngle = (-90.0 + index * 45.0) * PI / 180.0
            val labelCenter = Offset(
                x = center.x + cos(labelAngle).toFloat() * radius * 0.68f,
                y = center.y + sin(labelAngle).toFloat() * radius * 0.68f,
            )
            numberPaint.color = if (enabled && selectedMotor == motorNumber) {
                selectedTextColor.toArgb()
            } else {
                normalTextColor.toArgb()
            }
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    motorNumber.toString(),
                    labelCenter.x,
                    labelCenter.y - (numberPaint.ascent() + numberPaint.descent()) / 2f,
                    numberPaint,
                )
            }
        }

        drawCircle(color = stopColor, radius = radius * STOP_RADIUS_RATIO)
        val stopPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = stopTextColor.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 20.sp.toPx()
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(
                "STOP",
                center.x,
                center.y - (stopPaint.ascent() + stopPaint.descent()) / 2f,
                stopPaint,
            )
        }
    }
}

private fun MotorConnectionStatus.displayName(): String {
    return when (this) {
        MotorConnectionStatus.Disconnected -> "未连接"
        MotorConnectionStatus.Scanning -> "扫描中"
        MotorConnectionStatus.Connecting -> "连接中"
        MotorConnectionStatus.Connected -> "已连接"
        MotorConnectionStatus.Error -> "错误"
    }
}

private fun PhoneHeadingStatus.displayName(): String {
    return when (this) {
        PhoneHeadingStatus.Stopped -> "未启动"
        PhoneHeadingStatus.Available -> "可用"
        PhoneHeadingStatus.SensorUnavailable -> "手机不支持旋转矢量传感器"
        PhoneHeadingStatus.Unreliable -> "传感器不可靠，电机已停止"
        PhoneHeadingStatus.InvalidOrientation -> "手机姿态不符合要求，电机已停止"
    }
}

private fun PhoneHeadingAccuracy.displayName(): String {
    return when (this) {
        PhoneHeadingAccuracy.Unknown -> "未知"
        PhoneHeadingAccuracy.Low -> "低"
        PhoneHeadingAccuracy.Medium -> "中"
        PhoneHeadingAccuracy.High -> "高"
    }
}

private fun SimulationScenario.displayName(): String {
    return when (this) {
        SimulationScenario.Straight -> "直线路线"
        SimulationScenario.RightAngleTurn -> "直角转弯路线"
        SimulationScenario.AccuracyFilter -> "精度过滤路线"
    }
}

private fun SimulationScenario.description(): String {
    return when (this) {
        SimulationScenario.Straight -> "持续向东移动，每个点精度为 3 米。"
        SimulationScenario.RightAngleTurn -> "先向北再向东移动，用于检查轨迹和切线方向。"
        SimulationScenario.AccuracyFilter -> "交替发送 3 米和 15 米精度点，用于检查不合格点是否被丢弃。"
    }
}

private const val STOP_RADIUS_RATIO = 0.27f
private const val NORTH_TEST_ACCURACY_METERS = 3f

private val NORTH_ROUTE_TANGENT = RouteTangent(
    segmentStartIndex = 0,
    segmentEndIndex = 1,
    projectionRatio = 0.5,
    tangentBearingDegrees = 0.0,
    distanceToRouteMeters = 0.0,
    alongRouteDistanceMeters = 50.0,
    totalRouteDistanceMeters = 100.0,
)
