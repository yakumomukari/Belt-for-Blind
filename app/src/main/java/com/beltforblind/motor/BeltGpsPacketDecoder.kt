package com.beltforblind.motor

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

object BeltGpsPacketDecoder {
    const val PACKET_SIZE = 18
    private const val PROTOCOL_VERSION = 1
    private const val MIN_BASE_ACCURACY_METERS = 2.5f
    private const val HDOP_TO_ACCURACY_FACTOR = 2.5f

    fun decode(
        packet: ByteArray,
        receivedAtMillis: Long = System.currentTimeMillis(),
    ): BeltGpsSample? {
        if (packet.size != PACKET_SIZE) return null
        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        val version = buffer.get().toInt() and 0xff
        if (version != PROTOCOL_VERSION) return null

        val flags = buffer.get().toInt() and 0xff
        val sequence = buffer.short.toInt() and 0xffff
        val latitude = buffer.int / 10_000_000.0
        val longitude = buffer.int / 10_000_000.0
        val speedCmPerSecond = buffer.short.toInt() and 0xffff
        val hdopTimes100 = buffer.short.toInt() and 0xffff
        val satellites = buffer.get().toInt() and 0xff
        val fixQuality = buffer.get().toInt() and 0xff
        val fixValid = flags and 0x01 != 0 && fixQuality > 0

        if (fixValid && (latitude !in -90.0..90.0 || longitude !in -180.0..180.0)) {
            return null
        }

        val hdop = hdopTimes100.takeUnless { it == 0 || it == 0xffff }?.div(100f)
        val accuracy = hdop?.let {
            max(MIN_BASE_ACCURACY_METERS, it * HDOP_TO_ACCURACY_FACTOR)
        }
        val speed = speedCmPerSecond.takeUnless { it == 0xffff }?.div(100f)

        return BeltGpsSample(
            latitudeWgs84 = latitude,
            longitudeWgs84 = longitude,
            receivedAtMillis = receivedAtMillis,
            horizontalAccuracyMeters = accuracy,
            satelliteCount = satellites,
            hdop = hdop,
            speedMetersPerSecond = speed,
            fixQuality = fixQuality,
            sequence = sequence,
            isFixValid = fixValid,
        )
    }
}
