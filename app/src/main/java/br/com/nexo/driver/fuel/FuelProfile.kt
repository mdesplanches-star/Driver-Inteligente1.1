package br.com.nexo.driver.fuel

import java.util.UUID

enum class FuelType { GASOLINE, ETHANOL, DIESEL, CNG, ELECTRIC, FLEX }

/**
 * A named, independently persisted vehicle efficiency profile used to estimate consumption from
 * session distance. `consumptionKmPerUnit` is km per liter for combustion fuels and km per kWh
 * for [FuelType.ELECTRIC]; `fuelPricePerUnitCents` follows the same unit.
 */
data class FuelProfile(
    val id: String,
    val vehicleLabel: String,
    val fuelType: FuelType,
    val consumptionKmPerUnit: Double,
    val fuelPricePerUnitCents: Long? = null,
    val isEnabled: Boolean = true,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    init {
        require(id.isNotBlank()) { "Fuel profile id cannot be blank." }
        require(vehicleLabel.isNotBlank()) { "Vehicle label cannot be blank." }
        require(vehicleLabel.length <= MAX_LABEL_LENGTH) { "Vehicle label is too long." }
        require(consumptionKmPerUnit.isFinite() && consumptionKmPerUnit > 0.0) {
            "Consumption must be a positive, finite value."
        }
        require(fuelPricePerUnitCents == null || fuelPricePerUnitCents >= 0L) {
            "Fuel price cannot be negative."
        }
        require(createdAtEpochMs > 0) { "Creation time must be positive." }
        require(updatedAtEpochMs >= createdAtEpochMs) { "Update time cannot precede creation time." }
    }

    fun updated(
        vehicleLabel: String = this.vehicleLabel,
        fuelType: FuelType = this.fuelType,
        consumptionKmPerUnit: Double = this.consumptionKmPerUnit,
        fuelPricePerUnitCents: Long? = this.fuelPricePerUnitCents,
        isEnabled: Boolean = this.isEnabled,
        updatedAtEpochMs: Long,
    ) = copy(
        vehicleLabel = vehicleLabel.trim(),
        fuelType = fuelType,
        consumptionKmPerUnit = consumptionKmPerUnit,
        fuelPricePerUnitCents = fuelPricePerUnitCents,
        isEnabled = isEnabled,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    companion object {
        const val MAX_LABEL_LENGTH = 80

        fun create(
            vehicleLabel: String,
            fuelType: FuelType,
            consumptionKmPerUnit: Double,
            fuelPricePerUnitCents: Long? = null,
            nowEpochMs: Long,
            id: String = UUID.randomUUID().toString(),
        ) = FuelProfile(
            id = id,
            vehicleLabel = vehicleLabel.trim(),
            fuelType = fuelType,
            consumptionKmPerUnit = consumptionKmPerUnit,
            fuelPricePerUnitCents = fuelPricePerUnitCents,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs,
        )
    }
}

data class FuelProfileSnapshot(
    val profiles: List<FuelProfile>,
    val activeProfileId: String?,
) {
    init {
        require(activeProfileId == null || profiles.any { it.id == activeProfileId }) {
            "The active fuel profile must exist in the snapshot."
        }
    }

    val activeProfile: FuelProfile? get() = profiles.firstOrNull { it.id == activeProfileId }
}
