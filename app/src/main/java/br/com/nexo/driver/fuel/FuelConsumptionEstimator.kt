package br.com.nexo.driver.fuel

/** Estimated consumption (liters or kWh, per [FuelProfile.fuelType]) and cost for a session distance. */
data class FuelConsumptionEstimate(
    val distanceMeters: Double,
    val estimatedConsumptionUnits: Double?,
    val estimatedCostCents: Long?,
)

/**
 * Pure estimator: no public Android API exposes a real fuel-level sensor, so consumption is
 * derived from session distance (already tracked by [br.com.nexo.driver.location.CurrentLocationStateRepository])
 * and the driver's configured average efficiency. Never reads a sensor and never leaves the device.
 */
class FuelConsumptionEstimator {
    fun estimate(sessionDistanceMeters: Double, profile: FuelProfile?): FuelConsumptionEstimate {
        val distance = sessionDistanceMeters.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        if (profile == null || distance <= 0.0) {
            return FuelConsumptionEstimate(distance, null, null)
        }
        val distanceKm = distance / METERS_PER_KM
        val units = distanceKm / profile.consumptionKmPerUnit
        val cost = profile.fuelPricePerUnitCents?.let { pricePerUnitCents -> (units * pricePerUnitCents).toLong() }
        return FuelConsumptionEstimate(distance, units, cost)
    }

    private companion object {
        const val METERS_PER_KM = 1_000.0
    }
}
