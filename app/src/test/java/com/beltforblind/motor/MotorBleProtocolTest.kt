package com.beltforblind.motor

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MotorBleProtocolTest {
    @Test
    fun encodeIntensityCommand_writesHeaderAndEightUnsignedIntensities() {
        assertArrayEquals(
            byteArrayOf(
                0xA1.toByte(),
                0,
                32,
                64,
                96,
                128.toByte(),
                160.toByte(),
                224.toByte(),
                255.toByte(),
            ),
            MotorBleProtocol.encodeIntensityCommand(
                listOf(0, 32, 64, 96, 128, 160, 224, 255),
            ),
        )
    }

    @Test
    fun encodeIntensityCommand_rejectsWrongMotorCount() {
        assertThrows(IllegalArgumentException::class.java) {
            MotorBleProtocol.encodeIntensityCommand(List(7) { 0 })
        }
    }

    @Test
    fun encodeIntensityCommand_rejectsOutOfRangeIntensity() {
        assertThrows(IllegalArgumentException::class.java) {
            MotorBleProtocol.encodeIntensityCommand(
                listOf(0, 0, 0, 0, 0, 0, 0, 256),
            )
        }
    }
}
