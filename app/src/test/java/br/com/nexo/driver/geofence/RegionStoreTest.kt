package br.com.nexo.driver.geofence

import org.junit.Assert.assertEquals
import org.junit.Test

class RegionStoreTest {
    @Test
    fun `codec round trips regions`() {
        val region = Region.create(
            name = "Centro no fim de semana",
            centerLatitude = -25.4284,
            centerLongitude = -49.2733,
            type = RegionType.BAD,
            radiusMeters = 1_500.0,
            nowEpochMs = 1_000,
            id = "region-1",
        )

        assertEquals(listOf(region), RegionPayloadCodec.decode(RegionPayloadCodec.encode(listOf(region))))
    }

    @Test
    fun `codec ignores malformed records without losing valid regions`() {
        val valid = Region.create(
            name = "Válida",
            centerLatitude = -25.0,
            centerLongitude = -49.0,
            type = RegionType.GOOD,
            nowEpochMs = 1_000,
            id = "valid",
        )
        val payload = RegionPayloadCodec.encode(listOf(valid)) + "\ng\tbad\trow"

        assertEquals(listOf(valid), RegionPayloadCodec.decode(payload))
    }

    @Test
    fun `store replaces region with same id and removes on delete`() {
        val store = InMemoryRegionStore()
        val region = Region.create(
            name = "Bairro bom",
            centerLatitude = -25.0,
            centerLongitude = -49.0,
            type = RegionType.GOOD,
            nowEpochMs = 1_000,
            id = "region-1",
        )

        assertEquals(listOf(region), store.save(region).regions)
        val renamed = region.updated(name = "Bairro ótimo", updatedAtEpochMs = 2_000)
        assertEquals(listOf(renamed), store.save(renamed).regions)
        assertEquals(emptyList<Region>(), store.delete("region-1").regions)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `region rejects radius outside the allowed range`() {
        Region.create(
            name = "Raio inválido",
            centerLatitude = -25.0,
            centerLongitude = -49.0,
            type = RegionType.GOOD,
            radiusMeters = 50.0,
            nowEpochMs = 1_000,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `region rejects invalid latitude`() {
        Region.create(
            name = "Latitude inválida",
            centerLatitude = 200.0,
            centerLongitude = -49.0,
            type = RegionType.GOOD,
            nowEpochMs = 1_000,
        )
    }
}
