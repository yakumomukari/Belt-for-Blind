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
    val motorIntensities: List<Int> = List(MotorBleProtocol.MOTOR_COUNT) { 0 },
    val message: String? = null,
) {
    val isConnected: Boolean
        get() = status == MotorConnectionStatus.Connected
}

data class BeltGpsSample(
    val latitudeWgs84: Double,
    val longitudeWgs84: Double,
    val receivedAtMillis: Long,
    val horizontalAccuracyMeters: Float?,
    val satelliteCount: Int,
    val hdop: Float?,
    val speedMetersPerSecond: Float?,
    val fixQuality: Int,
    val sequence: Int,
    val isFixValid: Boolean,
)

interface MotorControlGateway {
    val state: StateFlow<MotorControlState>
    val gpsSample: StateFlow<BeltGpsSample?>

    fun scanAndConnect()

    fun activateMotor(motorNumber: Int)

    fun setMotorIntensities(intensities: List<Int>)

    fun stopMotor()

    fun disconnect()

    fun close()
}

object MotorBleProtocol {
    const val MOTOR_COUNT = 8
    const val MIN_INTENSITY = 0
    const val MAX_INTENSITY = 255
    const val VECTOR_COMMAND_HEADER = 0xA1
    const val VECTOR_COMMAND_SIZE = MOTOR_COUNT + 1

    const val DEVICE_NAME = "BeltMotor"
    val SERVICE_UUID: UUID = UUID.fromString("8f8a0001-8f4b-4f5b-9f2b-5e7a1f000001")
    val COMMAND_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("8f8a0002-8f4b-4f5b-9f2b-5e7a1f000001")
    val GPS_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("8f8a0003-8f4b-4f5b-9f2b-5e7a1f000001")

    fun encodeIntensityCommand(intensities: List<Int>): ByteArray {
        require(intensities.size == MOTOR_COUNT) {
            "Exactly $MOTOR_COUNT motor intensities are required."
        }
        require(intensities.all { it in MIN_INTENSITY..MAX_INTENSITY }) {
            "Motor intensities must be between $MIN_INTENSITY and $MAX_INTENSITY."
        }
        return ByteArray(VECTOR_COMMAND_SIZE).also { payload ->
            payload[0] = VECTOR_COMMAND_HEADER.toByte()
            intensities.forEachIndexed { index, intensity ->
                payload[index + 1] = intensity.toByte()
            }
        }
    }
}
