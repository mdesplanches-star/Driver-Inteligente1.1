package br.com.nexo.driver.profile

import br.com.nexo.driver.evaluation.Comparator
import br.com.nexo.driver.evaluation.FilterRule
import br.com.nexo.driver.evaluation.Metric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriverProfileStoreTest {
    @Test
    fun `codec round trips profiles and rules`() {
        val profile = DriverProfile.create(
            name = "Noite \u00e1gil",
            nowEpochMs = 1_000,
            id = "night",
            rules = listOf(
                FilterRule(Metric.RATE_PER_KM, Comparator.AT_LEAST, target = 175),
                FilterRule(Metric.HAS_MULTIPLE_STOPS, Comparator.IS_FALSE),
                FilterRule(Metric.TRIP_DURATION, Comparator.AT_LEAST, target = 300),
                FilterRule(Metric.TRIP_DURATION, Comparator.AT_MOST, target = 1_800),
                FilterRule(Metric.TOTAL_DISTANCE, Comparator.AT_LEAST, target = 2_000),
                FilterRule(Metric.TOTAL_DISTANCE, Comparator.AT_MOST, target = 12_000),
            ),
        )

        assertEquals(listOf(profile), ProfilePayloadCodec.decode(ProfilePayloadCodec.encode(listOf(profile))))
    }

    @Test
    fun `codec ignores malformed records without losing valid profiles`() {
        val valid = DriverProfile.create(name = "Dia", nowEpochMs = 1_000, id = "day")
        val payload = ProfilePayloadCodec.encode(listOf(valid)) + "\nr\tday\tUNKNOWN\tAT_LEAST\t100\t10\t1\tSCORE\t1"

        assertEquals(listOf(valid), ProfilePayloadCodec.decode(payload))
    }

    @Test
    fun `store selects first saved profile and clears selection on delete`() {
        val store = InMemoryDriverProfileStore()
        val profile = DriverProfile.create(name = "Padr\u00e3o", nowEpochMs = 1_000, id = "default")

        assertEquals("default", store.save(profile).activeProfileId)
        assertNull(store.delete("default").activeProfileId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `store refuses selection of unknown profile`() {
        InMemoryDriverProfileStore().setActiveProfile("missing")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `profile rejects only an exact metric comparator duplicate`() {
        DriverProfile.create(
            name = "Duplicado",
            nowEpochMs = 1_000,
            rules = listOf(
                FilterRule(Metric.TRIP_DURATION, Comparator.AT_LEAST, target = 300),
                FilterRule(Metric.TRIP_DURATION, Comparator.AT_LEAST, target = 600),
            ),
        )
    }
}
