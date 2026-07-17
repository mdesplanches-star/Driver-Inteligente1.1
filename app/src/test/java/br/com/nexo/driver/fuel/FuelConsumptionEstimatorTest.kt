package br.com.nexo.driver.fuel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelConsumptionEstimatorTest {
    private val estimator = FuelConsumptionEstimator()

    @Test
    fun `estimates liters and cost from session distance`() {
        val profile = FuelProfile.create(
            vehicleLabel = "Onix",
            fuelType = FuelType.FLEX,
            consumptionKmPerUnit = 10.0,
            fuelPricePerUnitCents = 500,
            nowEpochMs = 1_000,
        )

        val estimate = estimator.estimate(sessionDistanceMeters = 50_000.0, profile = profile)

        assertEquals(50_000.0, estimate.distanceMeters, 0.0)
        assertEquals(5.0, requireNotNull(estimate.estimatedConsumptionUnits), 1e-9)
        assertEquals(2_500L, estimate.estimatedCostCents)
    }

    @Test
    fun `omits cost when the profile has no configured price`() {
        val profile = FuelProfile.create(
            vehicleLabel = "Sem preço",
            fuelType = FuelType.GASOLINE,
            consumptionKmPerUnit = 10.0,
            nowEpochMs = 1_000,
        )

        val estimate = estimator.estimate(sessionDistanceMeters = 10_000.0, profile = profile)

        assertEquals(1.0, requireNotNull(estimate.estimatedConsumptionUnits), 1e-9)
        assertNull(estimate.estimatedCostCents)
    }

    @Test
    fun `returns no estimate without an active profile`() {
        val estimate = estimator.estimate(sessionDistanceMeters = 10_000.0, profile = null)

        assertEquals(10_000.0, estimate.distanceMeters, 0.0)
        assertNull(estimate.estimatedConsumptionUnits)
        assertNull(estimate.estimatedCostCents)
    }

    @Test
    fun `treats non finite or non positive distance as zero`() {
        val profile = FuelProfile.create(
            vehicleLabel = "Onix",
            fuelType = FuelType.GASOLINE,
            consumptionKmPerUnit = 10.0,
            nowEpochMs = 1_000,
        )

        val estimate = estimator.estimate(sessionDistanceMeters = Double.NaN, profile = profile)

        assertEquals(0.0, estimate.distanceMeters, 0.0)
        assertNull(estimate.estimatedConsumptionUnits)
    }
}
