package br.com.nexo.driver.overlay.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPreferencesTest {
    @Test
    fun `default grid contains the requested four metrics and no duplicate payment`() {
        assertEquals(
            listOf(
                OverlayMetricField.RATE_PER_KM,
                OverlayMetricField.RATE_PER_HOUR,
                OverlayMetricField.PASSENGER_RATING,
                OverlayMetricField.PICKUP,
            ),
            OverlayPreferences.DEFAULT.fields,
        )
        assertEquals(4, OverlayPreferences.DEFAULT.fields.distinct().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `configuration rejects duplicate fields`() {
        OverlayPreferences(
            listOf(
                OverlayMetricField.RATE_PER_KM,
                OverlayMetricField.RATE_PER_KM,
                OverlayMetricField.PASSENGER_RATING,
                OverlayMetricField.PICKUP,
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `replacing a slot with an already visible field is rejected`() {
        OverlayPreferences.DEFAULT.withField(OverlaySlot.TOP_START, OverlayMetricField.PICKUP)
    }

    @Test
    fun `codec round trips a valid alternative grid`() {
        val configuration = OverlayPreferences(
            listOf(
                OverlayMetricField.TOTAL_DISTANCE,
                OverlayMetricField.TOTAL_DURATION,
                OverlayMetricField.PASSENGER_RATING,
                OverlayMetricField.RATE_PER_KM,
            ),
        )

        assertEquals(configuration, OverlayPreferencesCodec.decode(OverlayPreferencesCodec.encode(configuration)))
    }

    @Test
    fun `codec uses defaults for corrupt and duplicate payloads`() {
        assertEquals(OverlayPreferences.DEFAULT, OverlayPreferencesCodec.decode("invalid"))
        assertEquals(
            OverlayPreferences.DEFAULT,
            OverlayPreferencesCodec.decode(
                "overlay-preferences-v1:RATE_PER_KM,RATE_PER_KM,PICKUP,PASSENGER_RATING",
            ),
        )
    }

    @Test
    fun `in memory store saves whole validated configurations`() {
        val store = InMemoryOverlayPreferenceStore()
        val replacement = OverlayPreferences(
            listOf(
                OverlayMetricField.TOTAL_DURATION,
                OverlayMetricField.TOTAL_DISTANCE,
                OverlayMetricField.PASSENGER_RATING,
                OverlayMetricField.PICKUP,
            ),
        )

        assertEquals(replacement, store.save(replacement))
        assertEquals(replacement, store.load())
        assertTrue(store.load().fields.all { it != OverlayMetricField.RATE_PER_HOUR })
    }
}
