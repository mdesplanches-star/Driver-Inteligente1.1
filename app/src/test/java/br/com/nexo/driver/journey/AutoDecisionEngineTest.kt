package br.com.nexo.driver.journey

import br.com.nexo.driver.evaluation.EvaluationResult
import br.com.nexo.driver.evaluation.OfferDecision
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoDecisionEngineTest {
    private val progress = DriverGoalProgress(
        realProfitPercent = 40,
        grossProfitPercent = 50,
        distancePercent = 30,
        onlinePercent = 60,
        mixedPercent = 45,
    )

    @Test
    fun `returns analyze when auto decision is off`() {
        val result = AutoDecisionEngine().decide(
            settings = AutoDecisionSettings(mode = AutoDecisionMode.OFF),
            evaluation = evaluation(OfferDecision.ACCEPT),
            estimatedRealProfitCents = 2_000,
            goalProgress = progress,
        )

        assertEquals(AutoDecisionAction.ANALYZE, result.action)
        assertEquals(AutoDecisionMode.OFF, result.mode)
    }

    @Test
    fun `would accept when filters and real profit pass`() {
        val result = AutoDecisionEngine().decide(
            settings = AutoDecisionSettings(
                mode = AutoDecisionMode.ASSISTIVE,
                minimumRealProfitCents = 1_500,
                autoAcceptEnabled = true,
            ),
            evaluation = evaluation(OfferDecision.ACCEPT),
            estimatedRealProfitCents = 2_000,
            goalProgress = progress,
        )

        assertEquals(AutoDecisionAction.WOULD_ACCEPT, result.action)
    }

    @Test
    fun `would reject when real profit is below minimum`() {
        val result = AutoDecisionEngine().decide(
            settings = AutoDecisionSettings(
                mode = AutoDecisionMode.ASSISTIVE,
                minimumRealProfitCents = 1_500,
                autoRejectEnabled = true,
            ),
            evaluation = evaluation(OfferDecision.ACCEPT),
            estimatedRealProfitCents = 1_000,
            goalProgress = progress,
        )

        assertEquals(AutoDecisionAction.WOULD_REJECT, result.action)
    }

    @Test
    fun `analyzes when matching auto action toggle is off`() {
        val result = AutoDecisionEngine().decide(
            settings = AutoDecisionSettings(mode = AutoDecisionMode.ASSISTIVE, autoAcceptEnabled = false),
            evaluation = evaluation(OfferDecision.ACCEPT),
            estimatedRealProfitCents = 2_000,
            goalProgress = progress,
        )

        assertEquals(AutoDecisionAction.ANALYZE, result.action)
    }

    @Test
    fun `analyzes when real profit is missing`() {
        val result = AutoDecisionEngine().decide(
            settings = AutoDecisionSettings(mode = AutoDecisionMode.TRAINING),
            evaluation = evaluation(OfferDecision.ACCEPT),
            estimatedRealProfitCents = null,
            goalProgress = progress,
        )

        assertEquals(AutoDecisionAction.ANALYZE, result.action)
    }

    @Test
    fun `calculates mixed goal progress with real profit as main signal`() {
        val summary = DailyDriverSummary(
            distanceMeters = 50_000.0,
            onlineMillis = 4 * 60 * 60 * 1_000L,
            rideMillis = 2 * 60 * 60 * 1_000L,
            grossProfitCents = 20_000,
            realProfitCents = 10_000,
            fuelCostCents = 3_000,
            extraCostCents = 1_000,
            analyzedCount = 0,
            acceptedCount = 0,
            rejectedCount = 0,
        )

        val calculated = DriverGoalProgressCalculator().calculate(
            summary,
            DriverGoalSettings(
                realProfitTargetCents = 20_000,
                grossProfitTargetCents = 40_000,
                distanceTargetMeters = 100_000.0,
                onlineTargetMillis = 8 * 60 * 60 * 1_000L,
            ),
        )

        assertEquals(50, calculated.realProfitPercent)
        assertEquals(50, calculated.grossProfitPercent)
        assertEquals(50, calculated.distancePercent)
        assertEquals(50, calculated.onlinePercent)
        assertEquals(50, calculated.mixedPercent)
    }

    private fun evaluation(decision: OfferDecision) = EvaluationResult(
        metrics = emptyList(),
        weightedScore = 100,
        decision = decision,
    )
}
