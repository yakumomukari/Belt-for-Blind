package com.beltforblind.ui.sport

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
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
import com.beltforblind.motor.MotorConnectionStatus
import com.beltforblind.motor.MotorControlGateway
import com.beltforblind.motor.MotorArcLayout
import com.beltforblind.route.location.LocationSimulationProvider
import com.beltforblind.route.location.SimulationScenario
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun DebugGpsScreen(
    onOpenRecordPage: () -> Unit,
    onClose: () -> Unit,
    motorController: MotorControlGateway,
    modifier: Modifier = Modifier,
) {
    val simulationState by LocationSimulationProvider.state.collectAsState()
    var selectedScenario by remember { mutableStateOf(simulationState.scenario) }
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
    var manualMotorJob by remember { mutableStateOf<Job?>(null) }
    var requestedIntensities by remember {
        mutableStateOf(List(MotorArcLayout.MOTOR_COUNT) { 0 })
    }
    var selectedRelativeAngle by remember { mutableStateOf<Double?>(null) }
    var maximumIntensity by remember { mutableFloatStateOf(DEFAULT_MAXIMUM_INTENSITY.toFloat()) }
    val coroutineScope = rememberCoroutineScope()
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
    val stopManualControl = {
        manualMotorJob?.cancel()
        manualMotorJob = null
        selectedRelativeAngle = null
        requestedIntensities = List(MotorArcLayout.MOTOR_COUNT) { 0 }
        controller.stopMotor()
    }
    val startManualControl: (Double) -> Unit = { relativeAngle ->
        val intensities = MotorArcLayout.intensitiesForRelativeAngle(
            relativeAngleDegrees = relativeAngle,
            maximumIntensity = maximumIntensity.roundToInt(),
        )
        selectedRelativeAngle = relativeAngle
        requestedIntensities = intensities
        manualMotorJob?.cancel()
        manualMotorJob = coroutineScope.launch {
            while (isActive) {
                controller.setMotorIntensities(intensities)
                delay(MOTOR_VECTOR_REFRESH_MILLIS)
            }
        }
    }

    DisposableEffect(controller) {
        onDispose {
            manualMotorJob?.cancel()
            controller.stopMotor()
        }
    }
    LaunchedEffect(state.isConnected) {
        if (!state.isConnected) {
            manualMotorJob?.cancel()
            manualMotorJob = null
            selectedRelativeAngle = null
            requestedIntensities = List(MotorArcLayout.MOTOR_COUNT) { 0 }
        }
    }

    Text("腰带电机测试", style = MaterialTheme.typography.titleLarge)
    Text("以正前方为对称轴，1 至 8 号电机从左向右排列。按住或沿圆弧拖动，相邻电机会按角度连续分配强度；松手立即停止。")
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
    Text("前侧控制圆弧", style = MaterialTheme.typography.titleLarge)
    Text(
        "最大强度：${maximumIntensity.roundToInt()} / 255（${(maximumIntensity / 255f * 100).roundToInt()}%）",
        style = MaterialTheme.typography.titleMedium,
    )
    Slider(
        value = maximumIntensity,
        onValueChange = { maximumIntensity = it },
        valueRange = MINIMUM_SELECTABLE_INTENSITY.toFloat()..255f,
        steps = 18,
        enabled = state.isConnected,
        modifier = Modifier.fillMaxWidth(),
    )
    MotorArcControl(
        motorIntensities = requestedIntensities,
        enabled = state.isConnected,
        onRelativeAngleChanged = startManualControl,
        onInteractionEnd = stopManualControl,
    )
    OutlinedButton(
        onClick = stopManualControl,
        enabled = state.isConnected,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("立即停止全部电机")
    }
    Text(
        text = selectedRelativeAngle?.let { angle ->
            "目标方向：${if (angle < 0) "左" else if (angle > 0) "右" else "正前方"} " +
                "${kotlin.math.abs(angle).toInt()}°"
        } ?: "目标方向：未控制",
        style = MaterialTheme.typography.titleMedium,
    )
    val confirmedOutputs = state.motorIntensities.mapIndexedNotNull { index, intensity ->
        intensity.takeIf { it > 0 }?.let {
            "${index + 1}号 ${(it / 255f * 100).roundToInt()}%"
        }
    }
    Text(
        text = if (confirmedOutputs.isEmpty()) {
            "实际输出：全部停止"
        } else {
            "实际输出：${confirmedOutputs.joinToString("，")}"
        },
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = "调试时请确保电机由驱动电路和独立电源供电，不要直接由 ESP32 GPIO 驱动。",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun MotorArcControl(
    motorIntensities: List<Int>,
    enabled: Boolean,
    onRelativeAngleChanged: (Double) -> Unit,
    onInteractionEnd: () -> Unit,
) {
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val disabledColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val labelColor = MaterialTheme.colorScheme.onSurface
    val selectedColor = MaterialTheme.colorScheme.primary
    val selectedNodeColor = MaterialTheme.colorScheme.onPrimary
    val nodeColor = MaterialTheme.colorScheme.surface
    val gradientColors = listOf(
        androidx.compose.ui.graphics.Color(0xFF1687B8),
        androidx.compose.ui.graphics.Color(0xFF5D5ADB),
        androidx.compose.ui.graphics.Color(0xFF9B3FA3),
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.45f)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics {
                contentDescription = if (enabled) {
                    val activeMotors = motorIntensities.mapIndexedNotNull { index, intensity ->
                        intensity.takeIf { it > 0 }?.let { "${index + 1}号$it" }
                    }
                    "前侧电机控制圆弧，当前${activeMotors.joinToString("，").ifEmpty { "全部停止" }}"
                } else {
                    "前侧电机控制圆弧未启用"
                }
            }
            .pointerInput(enabled) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!enabled) return@awaitEachGesture

                    val touchWidth = 72.dp.toPx()
                    var controlling = false
                    var lastAngle: Double? = null
                    try {
                        relativeAngleForArcPosition(
                            position = down.position,
                            width = size.width.toFloat(),
                            height = size.height.toFloat(),
                            touchWidth = touchWidth,
                        )?.let { angle ->
                            controlling = true
                            lastAngle = angle
                            onRelativeAngleChanged(angle)
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val angle = relativeAngleForArcPosition(
                                position = change.position,
                                width = size.width.toFloat(),
                                height = size.height.toFloat(),
                                touchWidth = touchWidth,
                            )
                            if (angle == null) {
                                if (controlling) {
                                    controlling = false
                                    lastAngle = null
                                    onInteractionEnd()
                                }
                            } else if (
                                    lastAngle == null ||
                                        kotlin.math.abs(angle - lastAngle!!) >= ARC_UPDATE_STEP_DEGREES
                            ) {
                                controlling = true
                                lastAngle = angle
                                onRelativeAngleChanged(angle)
                            }
                            change.consume()
                        }
                    } finally {
                        if (controlling) onInteractionEnd()
                    }
                }
            },
    ) {
        val arcCenter = Offset(size.width / 2f, size.height * ARC_CENTER_Y_RATIO)
        val radius = minOf(size.width * ARC_RADIUS_WIDTH_RATIO, size.height * ARC_RADIUS_HEIGHT_RATIO)
        val topLeft = Offset(arcCenter.x - radius, arcCenter.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)
        val startAngle = ARC_FORWARD_CANVAS_DEGREES.toFloat() +
            MotorArcLayout.MIN_RELATIVE_ANGLE_DEGREES.toFloat()
        val sweepAngle = (
            MotorArcLayout.MAX_RELATIVE_ANGLE_DEGREES -
                MotorArcLayout.MIN_RELATIVE_ANGLE_DEGREES
            ).toFloat()

        drawArc(
            color = outlineColor,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = 52.dp.toPx(), cap = StrokeCap.Round),
        )
        if (enabled) {
            drawArc(
                brush = Brush.horizontalGradient(
                    colors = gradientColors,
                    startX = arcCenter.x - radius,
                    endX = arcCenter.x + radius,
                ),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = 38.dp.toPx(), cap = StrokeCap.Round),
            )
        } else {
            drawArc(
                color = disabledColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = 38.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        val numberPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = labelColor.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 16.sp.toPx()
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val frontPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = labelColor.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 15.sp.toPx()
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val intensityPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = selectedColor.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 11.sp.toPx()
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        MotorArcLayout.relativeAnglesDegrees.forEachIndexed { index, relativeAngle ->
            val motorNumber = index + 1
            val intensity = motorIntensities.getOrElse(index) { 0 }
            val intensityRatio = intensity / 255f
            val radians = (
                ARC_FORWARD_CANVAS_DEGREES + relativeAngle
                ) * PI / 180.0
            val direction = Offset(cos(radians).toFloat(), sin(radians).toFloat())
            val node = arcCenter + direction * radius
            val isSelected = enabled && intensity > 0

            if (isSelected) {
                drawArc(
                    color = selectedColor.copy(alpha = 0.12f + intensityRatio * 0.28f),
                    startAngle = (
                        ARC_FORWARD_CANVAS_DEGREES + relativeAngle -
                            ARC_SELECTED_HALF_SWEEP_DEGREES
                        ).toFloat(),
                    sweepAngle = (ARC_SELECTED_HALF_SWEEP_DEGREES * 2).toFloat(),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = 62.dp.toPx(), cap = StrokeCap.Round),
                )
                drawCircle(
                    color = selectedColor.copy(alpha = 0.12f + intensityRatio * 0.20f),
                    radius = (20 + intensityRatio * 10).dp.toPx(),
                    center = node,
                )
            }
            drawCircle(
                color = if (isSelected) {
                    selectedNodeColor
                } else {
                    nodeColor
                },
                radius = if (isSelected) 12.dp.toPx() else 9.dp.toPx(),
                center = node,
            )
            drawCircle(
                color = if (isSelected) selectedColor else outlineColor,
                radius = if (isSelected) 7.dp.toPx() else 5.dp.toPx(),
                center = node,
            )

            val labelCenter = arcCenter + direction * (radius + 31.dp.toPx())
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    motorNumber.toString(),
                    labelCenter.x,
                    labelCenter.y - (numberPaint.ascent() + numberPaint.descent()) / 2f,
                    numberPaint,
                )
                if (intensity > 0) {
                    canvas.nativeCanvas.drawText(
                        "${(intensityRatio * 100).roundToInt()}%",
                        labelCenter.x,
                        labelCenter.y + 16.sp.toPx(),
                        intensityPaint,
                    )
                }
            }
        }

        val forwardNode = Offset(arcCenter.x, arcCenter.y - radius)
        drawLine(
            color = selectedColor.copy(alpha = 0.55f),
            start = Offset(forwardNode.x, forwardNode.y - 18.dp.toPx()),
            end = Offset(forwardNode.x, forwardNode.y + 18.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(
                "正前方",
                forwardNode.x,
                forwardNode.y - 28.dp.toPx(),
                frontPaint,
            )
        }
    }
}

private fun relativeAngleForArcPosition(
    position: Offset,
    width: Float,
    height: Float,
    touchWidth: Float,
): Double? {
    val center = Offset(width / 2f, height * ARC_CENTER_Y_RATIO)
    val radius = minOf(width * ARC_RADIUS_WIDTH_RATIO, height * ARC_RADIUS_HEIGHT_RATIO)
    val delta = position - center
    if (kotlin.math.abs(delta.getDistance() - radius) > touchWidth / 2f) return null

    val canvasAngle = Math.toDegrees(atan2(delta.y, delta.x).toDouble())
    var relativeAngle = canvasAngle - ARC_FORWARD_CANVAS_DEGREES
    while (relativeAngle > 180.0) relativeAngle -= 360.0
    while (relativeAngle <= -180.0) relativeAngle += 360.0
    if (relativeAngle !in ARC_TOUCH_MIN_DEGREES..ARC_TOUCH_MAX_DEGREES) return null
    return relativeAngle.coerceIn(
        MotorArcLayout.MIN_RELATIVE_ANGLE_DEGREES,
        MotorArcLayout.MAX_RELATIVE_ANGLE_DEGREES,
    )
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

private const val ARC_FORWARD_CANVAS_DEGREES = -90.0
private const val ARC_TOUCH_MIN_DEGREES = -88.0
private const val ARC_TOUCH_MAX_DEGREES = 88.0
private const val ARC_SELECTED_HALF_SWEEP_DEGREES = 8.0
private const val ARC_CENTER_Y_RATIO = 0.94f
private const val ARC_RADIUS_WIDTH_RATIO = 0.40f
private const val ARC_RADIUS_HEIGHT_RATIO = 0.70f
private const val ARC_UPDATE_STEP_DEGREES = 0.5
private const val MOTOR_VECTOR_REFRESH_MILLIS = 300L
private const val MINIMUM_SELECTABLE_INTENSITY = 64
private const val DEFAULT_MAXIMUM_INTENSITY = 128
