package br.com.nexo.driver.geofence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionMembershipEvaluatorTest {
    private val evaluator = RegionMembershipEvaluator()

    @Test
    fun `reports a point close to the center as inside the radius`() {
        val region = Region.create(
            name = "Perto",
            centerLatitude = 0.0,
            centerLongitude = 0.0,
            type = RegionType.GOOD,
            radiusMeters = 1_000.0,
            nowEpochMs = 1_000,
        )

        // ~0.005 degrees of latitude is roughly 556 meters, well inside a 1 km radius.
        val memberships = evaluator.evaluate(latitude = 0.005, longitude = 0.0, regions = listOf(region))

        assertTrue(memberships.single().isInside)
        assertEquals(556.0, memberships.single().distanceMeters, 5.0)
    }

    @Test
    fun `reports a point beyond the radius as outside`() {
        val region = Region.create(
            name = "Longe",
            centerLatitude = 0.0,
            centerLongitude = 0.0,
            type = RegionType.BAD,
            radiusMeters = 1_000.0,
            nowEpochMs = 1_000,
        )

        // ~0.02 degrees of latitude is roughly 2224 meters, outside a 1 km radius.
        val memberships = evaluator.evaluate(latitude = 0.02, longitude = 0.0, regions = listOf(region))

        assertEquals(false, memberships.single().isInside)
    }

    @Test
    fun `excludes disabled regions from the result`() {
        val enabled = Region.create(
            name = "Ativa",
            centerLatitude = 0.0,
            centerLongitude = 0.0,
            type = RegionType.GOOD,
            nowEpochMs = 1_000,
            id = "enabled",
        )
        val disabled = enabled.copy(id = "disabled", isEnabled = false)

        val memberships = evaluator.evaluate(latitude = 0.0, longitude = 0.0, regions = listOf(enabled, disabled))

        assertEquals(listOf("enabled"), memberships.map { it.region.id })
    }

    @Test
    fun `evaluates every enabled region independently for overlapping areas`() {
        val good = Region.create(
            name = "Boa",
            centerLatitude = 0.0,
            centerLongitude = 0.0,
            type = RegionType.GOOD,
            radiusMeters = 2_000.0,
            nowEpochMs = 1_000,
            id = "good",
        )
        val bad = Region.create(
            name = "Ruim",
            centerLatitude = 0.0,
            centerLongitude = 0.001,
            type = RegionType.BAD,
            radiusMeters = 2_000.0,
            nowEpochMs = 1_000,
            id = "bad",
        )

        val memberships = evaluator.evaluate(latitude = 0.0, longitude = 0.0, regions = listOf(good, bad))

        assertEquals(2, memberships.size)
        assertTrue(memberships.all { it.isInside })
    }
}
