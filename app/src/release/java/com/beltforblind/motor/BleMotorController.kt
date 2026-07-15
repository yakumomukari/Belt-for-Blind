package com.beltforblind.motor

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BleMotorController(@Suppress("UNUSED_PARAMETER") context: Context) : MotorControlGateway {
    private val mutableState = MutableStateFlow(
        MotorControlState(
            status = MotorConnectionStatus.Error,
            message = "电机测试仅在 Debug 构建中可用",
        ),
    )
    override val state: StateFlow<MotorControlState> = mutableState.asStateFlow()

    override fun scanAndConnect() = Unit

    override fun activateMotor(motorNumber: Int) = Unit

    override fun stopMotor() = Unit

    override fun disconnect() = Unit

    override fun close() = Unit
}
