package com.beltforblind.motor

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BeltGpsPacketDecoderTest {
    @Test
    fun decode_readsValidLittleEndianFix() {
        val packet = ByteBuffer.allocate(BeltGpsPacketDecoder.PACKET_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(1)
            .put(1)
            .putShort(42)
            .putInt(399_088_230)
            .putInt(1_163_974_700)
            .putShort(125)
            .putShort(80)
            .put(12)
            .put(1)
            .array()

        val sample = requireNotNull(BeltGpsPacketDecoder.decode(packet, 1234L))

        assertTrue(sample.isFixValid)
        assertEquals(39.908823, sample.latitudeWgs84, 0.0000001)
        assertEquals(116.39747, sample.longitudeWgs84, 0.0000001)
        assertEquals(42, sample.sequence)
        assertEquals(12, sample.satelliteCount)
        assertEquals(0.8f, sample.hdop ?: 0f, 0.001f)
        assertEquals(2.5f, sample.horizontalAccuracyMeters ?: 0f, 0.001f)
        assertEquals(1.25f, sample.speedMetersPerSecond ?: 0f, 0.001f)
        assertEquals(1234L, sample.receivedAtMillis)
    }

    @Test
    fun decode_preservesInvalidFixForFallbackDecision() {
        val packet = ByteBuffer.allocate(BeltGpsPacketDecoder.PACKET_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(1)
            .put(0)
            .putShort(7)
            .putInt(0)
            .putInt(0)
            .putShort((-1).toShort())
            .putShort((-1).toShort())
            .put(0)
            .put(0)
            .array()

        val sample = requireNotNull(BeltGpsPacketDecoder.decode(packet))

        assertFalse(sample.isFixValid)
        assertNull(sample.hdop)
        assertNull(sample.horizontalAccuracyMeters)
        assertNull(sample.speedMetersPerSecond)
    }

    @Test
    fun decode_rejectsWrongVersionAndLength() {
        assertNull(BeltGpsPacketDecoder.decode(ByteArray(3)))
        val packet = ByteArray(BeltGpsPacketDecoder.PACKET_SIZE)
        packet[0] = 2
        assertNull(BeltGpsPacketDecoder.decode(packet))
    }
}
