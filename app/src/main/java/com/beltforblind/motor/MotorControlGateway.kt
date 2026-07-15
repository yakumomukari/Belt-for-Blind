package com.beltforblind.motor

import java.util.UUID
import kotlinx.coroutines.flow.StateFlow

enum class MotorConnectionStatus {
    Disconnected,
    Scanning,
    Connecting,
    Connected,
    Error,
}

data class MotorControlState(
    val status: MotorConnectionStatus = MotorConnectionStatus.Disconnected,
    val deviceName: String? = null,
    val selectedMotor: Int? = null,
    val message: String? = null,
) {
    val isConnected: Boolean
        get() = status == MotorConnectionStatus.Connected
}

interface MotorControlGateway {
    val state: StateFlow<MotorControlState>

    fun scanAndConnect()

    fun activateMotor(motorNumber: Int)

    fun stopMotor()

    fun disconnect()

    fun close()
}

object MotorBleProtocol {
    const val DEVICE_NAME = "BeltMotor"
    val SERVICE_UUID: UUID = UUID.fromString("8f8a0001-8f4b-4f5b-9f2b-5e7a1f000001")
    val COMMAND_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("8f8a0002-8f4b-4f5b-9f2b-5e7a1f000001")
}
