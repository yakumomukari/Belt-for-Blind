package com.beltforblind.navigation.heading

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class PhoneHeadingStatus {
    Stopped,
    Available,
    SensorUnavailable,
    Unreliable,
    InvalidOrientation,
}

enum class PhoneHeadingAccuracy {
    Unknown,
    Low,
    Medium,
    High,
}

data class PhoneHeadingSample(
    val status: PhoneHeadingStatus = PhoneHeadingStatus.Stopped,
    val headingDegrees: Double? = null,
    val accuracy: PhoneHeadingAccuracy = PhoneHeadingAccuracy.Unknown,
)

interface PhoneHeadingProvider {
    fun start()

    fun stop()
}

class AndroidPhoneHeadingProvider(
    context: Context,
    private val onSample: (PhoneHeadingSample) -> Unit,
) : PhoneHeadingProvider, SensorEventListener {
    private val sensorManager = context.applicationContext
        .getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotationMatrix = FloatArray(9)
    private val headingFilter = CircularHeadingFilter()

    private var sensorAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_LOW
    private var lastEmissionTimestampNanos = 0L
    private var started = false

    override fun start() {
        if (started) return
        val sensor = rotationVectorSensor
        if (sensor == null) {
            onSample(PhoneHeadingSample(status = PhoneHeadingStatus.SensorUnavailable))
            return
        }

        headingFilter.reset()
        lastEmissionTimestampNanos = 0L
        started = sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_GAME,
        )
        if (!started) {
            onSample(PhoneHeadingSample(status = PhoneHeadingStatus.SensorUnavailable))
        }
    }

    override fun stop() {
        if (started) sensorManager.unregisterListener(this)
        started = false
        headingFilter.reset()
        lastEmissionTimestampNanos = 0L
        onSample(PhoneHeadingSample())
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!started || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        if (event.timestamp - lastEmissionTimestampNanos < EMISSION_INTERVAL_NANOS) return
        lastEmissionTimestampNanos = event.timestamp

        if (sensorAccuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            onSample(
                PhoneHeadingSample(
                    status = PhoneHeadingStatus.Unreliable,
                    accuracy = sensorAccuracy.toPhoneHeadingAccuracy(),
                ),
            )
            return
        }

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val rawHeading = PhoneBackHeadingCalculator.headingFromRotationMatrix(rotationMatrix)
        if (rawHeading == null) {
            onSample(
                PhoneHeadingSample(
                    status = PhoneHeadingStatus.InvalidOrientation,
                    accuracy = sensorAccuracy.toPhoneHeadingAccuracy(),
                ),
            )
            return
        }

        onSample(
            PhoneHeadingSample(
                status = PhoneHeadingStatus.Available,
                headingDegrees = headingFilter.add(rawHeading),
                accuracy = sensorAccuracy.toPhoneHeadingAccuracy(),
            ),
        )
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        sensorAccuracy = accuracy
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            onSample(
                PhoneHeadingSample(
                    status = PhoneHeadingStatus.Unreliable,
                    accuracy = PhoneHeadingAccuracy.Unknown,
                ),
            )
        }
    }

    private fun Int.toPhoneHeadingAccuracy(): PhoneHeadingAccuracy {
        return when (this) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> PhoneHeadingAccuracy.High
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> PhoneHeadingAccuracy.Medium
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> PhoneHeadingAccuracy.Low
            else -> PhoneHeadingAccuracy.Unknown
        }
    }

    private companion object {
        const val EMISSION_INTERVAL_NANOS = 200_000_000L
    }
}

object PhoneBackHeadingCalculator {
    fun headingFromRotationMatrix(rotationMatrix: FloatArray): Double? {
        require(rotationMatrix.size >= 9) { "Rotation matrix must contain at least 9 values." }

        // The phone is upright at the front of the body with its back facing forward.
        val forwardEast = -rotationMatrix[2].toDouble()
        val forwardNorth = -rotationMatrix[5].toDouble()
        val horizontalMagnitude = hypot(forwardEast, forwardNorth)
        if (!horizontalMagnitude.isFinite() || horizontalMagnitude < MIN_HORIZONTAL_MAGNITUDE) {
            return null
        }

        return Math.toDegrees(atan2(forwardEast, forwardNorth)).normalize360()
    }

    fun toTrueNorth(
        magneticHeadingDegrees: Double,
        declinationDegrees: Double,
    ): Double {
        require(magneticHeadingDegrees.isFinite()) { "Magnetic heading must be finite." }
        require(declinationDegrees.isFinite()) { "Declination must be finite." }
        return (magneticHeadingDegrees + declinationDegrees).normalize360()
    }

    private fun Double.normalize360(): Double = ((this % 360.0) + 360.0) % 360.0

    private const val MIN_HORIZONTAL_MAGNITUDE = 0.5
}

private class CircularHeadingFilter {
    private var filteredEast = 0.0
    private var filteredNorth = 0.0
    private var initialized = false

    fun add(headingDegrees: Double): Double {
        val radians = Math.toRadians(headingDegrees)
        val east = sin(radians)
        val north = cos(radians)
        if (!initialized) {
            filteredEast = east
            filteredNorth = north
            initialized = true
        } else {
            filteredEast = FILTER_ALPHA * east + (1.0 - FILTER_ALPHA) * filteredEast
            filteredNorth = FILTER_ALPHA * north + (1.0 - FILTER_ALPHA) * filteredNorth
        }
        return Math.toDegrees(atan2(filteredEast, filteredNorth)).let {
            ((it % 360.0) + 360.0) % 360.0
        }
    }

    fun reset() {
        filteredEast = 0.0
        filteredNorth = 0.0
        initialized = false
    }

    private companion object {
        const val FILTER_ALPHA = 0.25
    }
}
