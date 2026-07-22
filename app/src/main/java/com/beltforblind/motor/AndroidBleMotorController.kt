package com.beltforblind.motor

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@SuppressLint("MissingPermission")
class AndroidBleMotorController(context: Context) : MotorControlGateway {
    private val applicationContext = context.applicationContext
    private val bluetoothManager =
        applicationContext.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter
        get() = bluetoothManager?.adapter
    private val mainHandler = Handler(Looper.getMainLooper())

    private val mutableState = MutableStateFlow(MotorControlState())
    override val state: StateFlow<MotorControlState> = mutableState.asStateFlow()
    override val gpsSample: StateFlow<BeltGpsSample?> = BeltGpsRepository.sample

    private var bluetoothGatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var gpsCharacteristic: BluetoothGattCharacteristic? = null
    private var inFlightCommand: PendingMotorCommand? = null
    private var queuedCommand: PendingMotorCommand? = null

    private val scanTimeout = Runnable {
        if (mutableState.value.status == MotorConnectionStatus.Scanning) {
            stopScanning()
            mutableState.value = MotorControlState(
                status = MotorConnectionStatus.Error,
                message = "未发现腰带，请确认设备已上电并正在广播",
            )
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (mutableState.value.status != MotorConnectionStatus.Scanning) return

            stopScanning()
            val deviceName = result.scanRecord?.deviceName ?: MotorBleProtocol.DEVICE_NAME
            mutableState.value = MotorControlState(
                status = MotorConnectionStatus.Connecting,
                deviceName = deviceName,
                message = "正在连接",
            )
            try {
                bluetoothGatt = result.device.connectGatt(
                    applicationContext,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE,
                )
            } catch (error: SecurityException) {
                setError("缺少蓝牙连接权限")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            stopScanning()
            setError("蓝牙扫描失败，错误码：$errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                bluetoothGatt = gatt
                mutableState.value = mutableState.value.copy(
                    status = MotorConnectionStatus.Connecting,
                    message = "正在读取电机控制服务",
                )
                if (!gatt.discoverServices()) {
                    setError("无法读取腰带服务")
                    closeGatt(gatt)
                }
                return
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                closeGatt(gatt)
                mutableState.value = MotorControlState(
                    status = if (status == BluetoothGatt.GATT_SUCCESS) {
                        MotorConnectionStatus.Disconnected
                    } else {
                        MotorConnectionStatus.Error
                    },
                    deviceName = mutableState.value.deviceName,
                    message = if (status == BluetoothGatt.GATT_SUCCESS) {
                        "连接已断开，电机已停止"
                    } else {
                        "蓝牙连接异常，状态码：$status"
                    },
                )
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                setError("读取腰带服务失败，状态码：$status")
                closeGatt(gatt)
                return
            }

            val service: BluetoothGattService? = gatt.getService(MotorBleProtocol.SERVICE_UUID)
            val characteristic = service?.getCharacteristic(MotorBleProtocol.COMMAND_CHARACTERISTIC_UUID)
            if (characteristic == null) {
                setError("设备未提供兼容的电机控制服务")
                closeGatt(gatt)
                return
            }

            commandCharacteristic = characteristic
            gpsCharacteristic = service.getCharacteristic(MotorBleProtocol.GPS_CHARACTERISTIC_UUID)
            val gpsNotificationsStarted = gpsCharacteristic?.let { gps ->
                enableGpsNotifications(gatt, gps)
            }
            mutableState.value = MotorControlState(
                status = MotorConnectionStatus.Connected,
                deviceName = mutableState.value.deviceName ?: MotorBleProtocol.DEVICE_NAME,
                message = when (gpsNotificationsStarted) {
                    null -> "腰带已连接（固件未提供 GPS）"
                    true -> "腰带已连接，正在等待外置 GPS"
                    false -> "腰带已连接，GPS 通知启用失败"
                },
            )
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (descriptor.uuid != CLIENT_CHARACTERISTIC_CONFIGURATION_UUID) return
            finishConnection(
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    "\u8170\u5e26\u5df2\u8fde\u63a5\uff0c\u5916\u7f6e GPS \u5df2\u542f\u7528"
                } else {
                    "\u8170\u5e26\u5df2\u8fde\u63a5\uff0cGPS \u901a\u77e5\u542f\u7528\u5931\u8d25"
                },
            )
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleGpsNotification(characteristic, value)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            handleGpsNotification(characteristic, characteristic.value ?: return)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (characteristic.uuid != MotorBleProtocol.COMMAND_CHARACTERISTIC_UUID) return

            val command = inFlightCommand
            inFlightCommand = null
            if (status == BluetoothGatt.GATT_SUCCESS && command != null) {
                val strongestMotor = command.intensities
                    .withIndex()
                    .filter { it.value > 0 }
                    .maxByOrNull { it.value }
                    ?.index
                    ?.plus(1)
                val activeDescription = command.intensities
                    .mapIndexedNotNull { index, intensity ->
                        intensity.takeIf { it > 0 }?.let { "${index + 1}号:$it" }
                    }
                    .joinToString("，")
                mutableState.value = mutableState.value.copy(
                    selectedMotor = strongestMotor,
                    motorIntensities = command.intensities,
                    message = if (strongestMotor == null) {
                        "全部电机已停止"
                    } else {
                        "电机强度：$activeDescription"
                    },
                )
            } else {
                mutableState.value = mutableState.value.copy(
                    message = "命令发送失败，状态码：$status",
                )
            }

            queuedCommand?.let { nextCommand ->
                queuedCommand = null
                writeMotorCommand(nextCommand)
            }
        }
    }

    override fun scanAndConnect() {
        disconnectInternal(updateState = false)

        val adapter = bluetoothAdapter
        if (adapter == null) {
            setError("此设备不支持蓝牙")
            return
        }
        if (!adapter.isEnabled) {
            setError("请先打开系统蓝牙")
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            setError("蓝牙扫描器不可用")
            return
        }

        mutableState.value = MotorControlState(
            status = MotorConnectionStatus.Scanning,
            message = "正在扫描 ${MotorBleProtocol.DEVICE_NAME}",
        )
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(MotorBleProtocol.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(listOf(filter), settings, scanCallback)
            mainHandler.postDelayed(scanTimeout, SCAN_TIMEOUT_MS)
        } catch (error: SecurityException) {
            setError("缺少蓝牙扫描权限")
        }
    }

    override fun activateMotor(motorNumber: Int) {
        require(motorNumber in 1..MotorBleProtocol.MOTOR_COUNT) {
            "Motor number must be between 1 and ${MotorBleProtocol.MOTOR_COUNT}"
        }
        val intensities = List(MotorBleProtocol.MOTOR_COUNT) { index ->
            if (index == motorNumber - 1) LEGACY_MOTOR_INTENSITY else 0
        }
        writeMotorCommand(
            PendingMotorCommand(
                payload = motorNumber.toString().encodeToByteArray(),
                intensities = intensities,
            ),
        )
    }

    override fun setMotorIntensities(intensities: List<Int>) {
        val snapshot = intensities.toList()
        writeMotorCommand(
            PendingMotorCommand(
                payload = MotorBleProtocol.encodeIntensityCommand(snapshot),
                intensities = snapshot,
            ),
        )
    }

    override fun stopMotor() {
        writeMotorCommand(
            PendingMotorCommand(
                payload = "0".encodeToByteArray(),
                intensities = List(MotorBleProtocol.MOTOR_COUNT) { 0 },
            ),
        )
    }

    override fun disconnect() {
        disconnectInternal(updateState = true)
    }

    override fun close() {
        disconnectInternal(updateState = false)
    }

    private fun writeMotorCommand(command: PendingMotorCommand) {
        if (inFlightCommand != null) {
            queuedCommand = command
            return
        }

        val gatt = bluetoothGatt
        val characteristic = commandCharacteristic
        if (gatt == null || characteristic == null || !mutableState.value.isConnected) {
            mutableState.value = mutableState.value.copy(message = "请先连接腰带")
            return
        }

        inFlightCommand = command
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                command.payload,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            characteristic.value = command.payload
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }

        if (!started) {
            inFlightCommand = null
            mutableState.value = mutableState.value.copy(message = "蓝牙正忙，请重试")
        }
    }

    private fun stopScanning() {
        mainHandler.removeCallbacks(scanTimeout)
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {
            // Permission may have been revoked while scanning.
        }
    }

    private fun enableGpsNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
    ): Boolean {
        if (!gatt.setCharacteristicNotification(characteristic, true)) return false
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIGURATION_UUID)
            ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(
                descriptor,
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun handleGpsNotification(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        if (characteristic.uuid != MotorBleProtocol.GPS_CHARACTERISTIC_UUID) return
        BeltGpsPacketDecoder.decode(value)?.let(BeltGpsRepository::publish)
    }

    private fun finishConnection(message: String) {
        mutableState.value = mutableState.value.copy(
            status = MotorConnectionStatus.Connected,
            deviceName = mutableState.value.deviceName ?: MotorBleProtocol.DEVICE_NAME,
            message = message,
        )
    }

    private fun disconnectInternal(updateState: Boolean) {
        stopScanning()
        inFlightCommand = null
        queuedCommand = null
        commandCharacteristic = null
        gpsCharacteristic = null
        BeltGpsRepository.clear()
        bluetoothGatt?.let { gatt ->
            try {
                gatt.disconnect()
            } catch (_: SecurityException) {
                // Closing the GATT still releases the local connection.
            }
            closeGatt(gatt)
        }
        bluetoothGatt = null
        if (updateState) {
            mutableState.value = MotorControlState(
                status = MotorConnectionStatus.Disconnected,
                message = "连接已断开，电机已停止",
            )
        }
    }

    private fun closeGatt(gatt: BluetoothGatt) {
        if (bluetoothGatt === gatt) bluetoothGatt = null
        commandCharacteristic = null
        gpsCharacteristic = null
        BeltGpsRepository.clear()
        gatt.close()
    }

    private fun setError(message: String) {
        mutableState.value = MotorControlState(
            status = MotorConnectionStatus.Error,
            deviceName = mutableState.value.deviceName,
            message = message,
        )
    }

    private companion object {
        const val LEGACY_MOTOR_INTENSITY = 128
        const val SCAN_TIMEOUT_MS = 10_000L
        val CLIENT_CHARACTERISTIC_CONFIGURATION_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

private data class PendingMotorCommand(
    val payload: ByteArray,
    val intensities: List<Int>,
)
