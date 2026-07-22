package com.beltforblind.route.location

import com.beltforblind.motor.BeltGpsSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Wgs84ToGcj02ConverterTest {
    @Test
    fun convert_movesBeijingCoordinateIntoGcj02() {
        val converted = Wgs84ToGcj02Converter.convert(39.908823, 116.397470)

        assertEquals(39.9102, converted.latitude, 0.001)
        assertEquals(116.4037, converted.longitude, 0.001)
    }

    @Test
    fun convert_leavesCoordinateOutsideChinaUnchanged() {
        val converted = Wgs84ToGcj02Converter.convert(48.8566, 2.3522)

        assertEquals(48.8566, converted.latitude, 0.0)
        assertEquals(2.3522, converted.longitude, 0.0)
    }

    @Test
    fun mapper_rejectsInvalidFixAndKeepsEstimatedAccuracy() {
        val invalid = sample(valid = false)
        assertNull(BeltGpsRoutePointMapper.toRoutePoint(invalid))

        val point = requireNotNull(BeltGpsRoutePointMapper.toRoutePoint(sample(valid = true)))
        assertEquals(3.5f, point.accuracy ?: 0f, 0f)
        assertTrue(point.longitude > 116.397470)
    }

    private fun sample(valid: Boolean) = BeltGpsSample(
        latitudeWgs84 = 39.908823,
        longitudeWgs84 = 116.397470,
        receivedAtMillis = 1000L,
        horizontalAccuracyMeters = 3.5f,
        satelliteCount = 10,
        hdop = 1.4f,
        speedMetersPerSecond = 1f,
        fixQuality = if (valid) 1 else 0,
        sequence = 1,
        isFixValid = valid,
    )
}
