package br.com.nexo.driver.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoMathTest {
    @Test
    fun `distance between an identical point is zero`() {
        assertEquals(0.0, GeoMath.haversineMeters(-25.4284, -49.2733, -25.4284, -49.2733), 1e-6)
    }

    @Test
    fun `one degree of latitude is roughly 111 kilometers`() {
        val distance = GeoMath.haversineMeters(0.0, 0.0, 1.0, 0.0)

        assertEquals(111_195.0, distance, 50.0)
    }

    @Test
    fun `distance is symmetric`() {
        val forward = GeoMath.haversineMeters(-25.4284, -49.2733, -23.5505, -46.6333)
        val backward = GeoMath.haversineMeters(-23.5505, -46.6333, -25.4284, -49.2733)

        assertEquals(forward, backward, 1e-6)
    }
}
