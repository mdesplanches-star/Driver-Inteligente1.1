package br.com.nexo.driver.fuel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelProfileStoreTest {
    @Test
    fun `codec round trips profiles`() {
        val profile = FuelProfile.create(
            vehicleLabel = "Onix 1.0 flex",
            fuelType = FuelType.FLEX,
            consumptionKmPerUnit = 12.5,
            fuelPricePerUnitCents = 599,
            nowEpochMs = 1_000,
            id = "car-1",
        )

        assertEquals(listOf(profile), FuelProfilePayloadCodec.decode(FuelProfilePayloadCodec.encode(listOf(profile))))
    }

    @Test
    fun `codec round trips a profile without a configured fuel price`() {
        val profile = FuelProfile.create(
            vehicleLabel = "Bike elétrica",
            fuelType = FuelType.ELECTRIC,
            consumptionKmPerUnit = 6.0,
            nowEpochMs = 1_000,
            id = "bike-1",
        )

        val decoded = FuelProfilePayloadCodec.decode(FuelProfilePayloadCodec.encode(listOf(profile)))

        assertEquals(listOf(profile), decoded)
        assertNull(decoded.single().fuelPricePerUnitCents)
    }

    @Test
    fun `codec ignores malformed records without losing valid profiles`() {
        val valid = FuelProfile.create(
            vehicleLabel = "Válido",
            fuelType = FuelType.GASOLINE,
            consumptionKmPerUnit = 10.0,
            nowEpochMs = 1_000,
            id = "valid",
        )
        val payload = FuelProfilePayloadCodec.encode(listOf(valid)) + "\nf\tbad\trow"

        assertEquals(listOf(valid), FuelProfilePayloadCodec.decode(payload))
    }

    @Test
    fun `store selects first saved profile and clears selection on delete`() {
        val store = InMemoryFuelProfileStore()
        val profile = FuelProfile.create(
            vehicleLabel = "Padrão",
            fuelType = FuelType.GASOLINE,
            consumptionKmPerUnit = 10.0,
            nowEpochMs = 1_000,
            id = "default",
        )

        assertEquals("default", store.save(profile).activeProfileId)
        assertNull(store.delete("default").activeProfileId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `store refuses selection of unknown profile`() {
        InMemoryFuelProfileStore().setActiveProfile("missing")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `profile rejects non positive consumption`() {
        FuelProfile.create(
            vehicleLabel = "Invalido",
            fuelType = FuelType.GASOLINE,
            consumptionKmPerUnit = 0.0,
            nowEpochMs = 1_000,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `profile rejects negative fuel price`() {
        FuelProfile.create(
            vehicleLabel = "Invalido",
            fuelType = FuelType.GASOLINE,
            consumptionKmPerUnit = 10.0,
            fuelPricePerUnitCents = -1,
            nowEpochMs = 1_000,
        )
    }
}
