package com.beltforblind.motor

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
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

    private var bluetoothGatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var inFlightCommand: Int? = null
    private var queuedCommand: Int? = null

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
            mutableState.value = MotorControlState(
                status = MotorConnectionStatus.Connected,
                deviceName = mutableState.value.deviceName ?: MotorBleProtocol.DEVICE_NAME,
                message = "腰带已连接",
            )
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
                mutableState.value = mutableState.value.copy(
                    selectedMotor = command.takeIf { it != 0 },
                    message = if (command == 0) "全部电机已停止" else "正在控制 $command 号电机",
                )
            } else {
                mutableState.value = mutableState.value.copy(
                    message = "命令发送失败，状态码：$status",
                )
            }

            queuedCommand?.let { nextCommand ->
                queuedCommand = null
                writeCommand(nextCommand)
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
        require(motorNumber in 1..8) { "Motor number must be between 1 and 8" }
        writeCommand(motorNumber)
    }

    override fun stopMotor() {
        writeCommand(0)
    }

    override fun disconnect() {
        disconnectInternal(updateState = true)
    }

    override fun close() {
        disconnectInternal(updateState = false)
    }

    private fun writeCommand(command: Int) {
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
        val value = command.toString().encodeToByteArray()
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                value,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            characteristic.value = value
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

    private fun disconnectInternal(updateState: Boolean) {
        stopScanning()
        inFlightCommand = null
        queuedCommand = null
        commandCharacteristic = null
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
        const val SCAN_TIMEOUT_MS = 10_000L
    }
}
