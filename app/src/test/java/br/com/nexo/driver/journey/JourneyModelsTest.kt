package br.com.nexo.driver.journey

import br.com.nexo.driver.offer.OfferSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyModelsTest {
    @Test
    fun `calculates real profit with fuel and detailed costs`() {
        val settings = DailyDriverCostSettings(
            fuelEfficiencyKmPerLiter = 10.0,
            fuelPriceCentsPerLiter = 600,
            maintenanceCents = 500,
            tireCents = 300,
            washCents = 200,
            platformFeeCents = 100,
            otherCents = 50,
        )

        val calculator = DailyDriverSummaryCalculator()

        assertEquals(1_200, calculator.fuelCostCents(distanceMeters = 20_000.0, settings))
        assertEquals(7_650, calculator.realProfitCents(grossCents = 10_000, distanceMeters = 20_000.0, settings))
    }

    @Test
    fun `tracks online and ride clocks independently`() {
        val state = RideSessionClockState()
            .withReaderActive(true, nowEpochMs = 1_000)
            .withRideActive(true, nowEpochMs = 2_000)
            .withRideActive(false, nowEpochMs = 8_000)
            .withReaderActive(false, nowEpochMs = 11_000)

        assertEquals(10_000, state.onlineMillis(nowEpochMs = 20_000))
        assertEquals(6_000, state.rideMillis(nowEpochMs = 20_000))
    }

    @Test
    fun `history codec round trips sanitized local entries`() {
        val entries = listOf(
            RideHistoryEntry(
                id = "ride-1",
                detectedAtEpochMs = 1_700_000_000_000,
                decision = RideHistoryDecision.ACCEPT,
                source = OfferSource.UBER,
                grossCents = 2590,
                totalDistanceMeters = 10_300,
                totalDurationSeconds = 1_320,
                pickupAddress = "Av. Paulista, 1200",
                dropoffAddress = "Rua das Palmeiras, 88",
                estimatedRealProfitCents = 1990,
                status = RideHistoryStatus.ACCEPTED,
                autoDecision = AutoDecisionAction.WOULD_ACCEPT,
                autoDecisionMode = AutoDecisionMode.TRAINING,
            ),
        )

        val encoded = RideHistoryPayloadCodec.encode(entries)
        val decoded = RideHistoryPayloadCodec.decode(encoded)

        assertEquals(entries, decoded)
        assertFalse(encoded.contains("raw_ocr", ignoreCase = true))
        assertFalse(encoded.contains("bitmap", ignoreCase = true))
    }

    @Test
    fun `summary counts accepted analyzed and rejected rides`() {
        val history = listOf(
            entry("a", RideHistoryStatus.ACCEPTED, 1_000),
            entry("b", RideHistoryStatus.ANALYZED, 2_000),
            entry("c", RideHistoryStatus.REJECTED, 3_000),
        )

        val summary = DailyDriverSummaryCalculator().summarize(
            distanceMeters = 10_000.0,
            clock = RideSessionClockState(accumulatedOnlineMillis = 60_000),
            history = history,
            settings = DailyDriverCostSettings(fuelEfficiencyKmPerLiter = 10.0, fuelPriceCentsPerLiter = 500),
            nowEpochMs = 10_000,
        )

        assertEquals(1, summary.acceptedCount)
        assertEquals(1, summary.analyzedCount)
        assertEquals(1, summary.rejectedCount)
        assertEquals(1_000, summary.grossProfitCents)
        assertTrue(summary.realProfitCents < summary.grossProfitCents)
    }

    private fun entry(id: String, status: RideHistoryStatus, gross: Long) = RideHistoryEntry(
        id = id,
        detectedAtEpochMs = gross,
        decision = when (status) {
            RideHistoryStatus.REJECTED -> RideHistoryDecision.REJECT
            RideHistoryStatus.ANALYZED -> RideHistoryDecision.ANALYZE
            else -> RideHistoryDecision.ACCEPT
        },
        source = OfferSource.UBER,
        grossCents = gross,
        totalDistanceMeters = 1_000,
        totalDurationSeconds = 600,
        pickupAddress = null,
        dropoffAddress = null,
        estimatedRealProfitCents = null,
        status = status,
    )
}
