package br.com.nexo.driver.overlay

import br.com.nexo.driver.evaluation.EvaluationResult
import br.com.nexo.driver.evaluation.Metric
import br.com.nexo.driver.evaluation.MetricStatus
import br.com.nexo.driver.evaluation.OfferDecision
import br.com.nexo.driver.evaluation.OfferEvaluator
import br.com.nexo.driver.offer.NormalizedOffer
import br.com.nexo.driver.overlay.preferences.OverlayMetricField
import br.com.nexo.driver.overlay.preferences.OverlayPreferences
import java.text.NumberFormat
import java.util.Locale

/** Converts evaluator output to the small, presentation-ready overlay model. */
class OfferOverlayPresenter(
    private val evaluator: OfferEvaluator = OfferEvaluator(),
) {
    fun present(
        offer: NormalizedOffer,
        result: EvaluationResult,
        gridFields: List<OverlayMetricField> = OverlayPreferences.DEFAULT.fields,
    ): OfferOverlayUiModel {
        val derived = evaluator.derive(offer)
        val payoutAvailable = offer.payout.isUsable()
        val ratePerKmAvailable = derived.ratePerKm.isUsable()
        val ratePerHourAvailable = derived.ratePerHour.isUsable()
        val passengerRatingAvailable = offer.passenger.rating.isUsable()
        val pickupAvailable = offer.pickup.duration.isUsable() && offer.pickup.distance.isUsable()
        return OfferOverlayUiModel(
            status = result.decision.toOverlayStatus(),
            totalDuration = derived.totalDuration.value?.seconds?.formatDuration() ?: "—",
            payout = if (payoutAvailable) offer.payout.value?.cents?.formatBrl() ?: "—" else "—",
            payoutStatus = result.statusFor(Metric.PAYOUT, payoutAvailable),
            isPayoutAvailable = payoutAvailable,
            ratePerKm = OverlayMetricUi(
                value = if (ratePerKmAvailable) derived.ratePerKm.value?.formatBrl() ?: "—" else "—",
                status = result.statusFor(Metric.RATE_PER_KM, ratePerKmAvailable),
                isAvailable = ratePerKmAvailable,
            ),
            ratePerHour = OverlayMetricUi(
                value = if (ratePerHourAvailable) derived.ratePerHour.value?.formatBrl() ?: "—" else "—",
                status = result.statusFor(Metric.RATE_PER_HOUR, ratePerHourAvailable),
                isAvailable = ratePerHourAvailable,
            ),
            passengerRating = OverlayMetricUi(
                value = if (passengerRatingAvailable) offer.passenger.rating.value?.formatRating() ?: "—" else "—",
                status = result.statusFor(Metric.PASSENGER_RATING, passengerRatingAvailable),
                isAvailable = passengerRatingAvailable,
            ),
            pickup = OverlayMetricUi(
                value = if (pickupAvailable) {
                    listOfNotNull(
                        offer.pickup.duration.value?.seconds?.formatDuration(),
                        offer.pickup.distance.value?.meters?.formatDistance(),
                    ).joinToString(" · ").ifBlank { "—" }
                } else {
                    "—"
                },
                status = pickupStatus(result, pickupAvailable),
                isAvailable = pickupAvailable,
            ),
            totalDurationMetric = OverlayMetricUi(
                value = derived.totalDuration.value?.seconds?.formatDuration() ?: "—",
                status = result.statusFor(Metric.TOTAL_DURATION, derived.totalDuration.isUsable()),
                isAvailable = derived.totalDuration.isUsable(),
            ),
            totalDistance = OverlayMetricUi(
                value = derived.totalDistance.value?.meters?.formatDistance() ?: "—",
                status = result.statusFor(Metric.TOTAL_DISTANCE, derived.totalDistance.isUsable()),
                isAvailable = derived.totalDistance.isUsable(),
            ),
            gridFields = gridFields,
            isTowardHome = offer.endsNearHome.value == true,
        )
    }

    private fun pickupStatus(result: EvaluationResult, isAvailable: Boolean): OverlayStatus = when {
        !isAvailable -> OverlayStatus.UNKNOWN
        result.metrics.any { it.rule.metric == Metric.PICKUP_DURATION && it.status == MetricStatus.FAIL } -> OverlayStatus.REJECT
        result.metrics.any { it.rule.metric == Metric.PICKUP_DISTANCE && it.status == MetricStatus.FAIL } -> OverlayStatus.REJECT
        result.metrics.any { it.rule.metric in PICKUP_METRICS && it.status in setOf(MetricStatus.NEAR, MetricStatus.UNKNOWN) } -> OverlayStatus.ANALYZE
        result.metrics.any { it.rule.metric in PICKUP_METRICS && it.status == MetricStatus.PASS } -> OverlayStatus.ACCEPT
        else -> OverlayStatus.UNKNOWN
    }

    /**
     * A metric may have both a minimum and a maximum rule. The cell must communicate the most
     * restrictive outcome across them: failure wins, then near-threshold, then unknown, then pass.
     */
    private fun EvaluationResult.statusFor(metric: Metric, isAvailable: Boolean): OverlayStatus {
        if (!isAvailable) return OverlayStatus.UNKNOWN
        val statuses = metrics.filter { it.rule.metric == metric }.map { it.status }
        return when {
            statuses.isEmpty() -> OverlayStatus.UNKNOWN
            MetricStatus.FAIL in statuses -> OverlayStatus.REJECT
            MetricStatus.NEAR in statuses -> OverlayStatus.ANALYZE
            MetricStatus.UNKNOWN in statuses -> OverlayStatus.UNKNOWN
            else -> OverlayStatus.ACCEPT
        }
    }

    private fun MetricStatus.toOverlayStatus(): OverlayStatus = when (this) {
        MetricStatus.PASS -> OverlayStatus.ACCEPT
        MetricStatus.NEAR -> OverlayStatus.ANALYZE
        MetricStatus.FAIL -> OverlayStatus.REJECT
        MetricStatus.UNKNOWN -> OverlayStatus.UNKNOWN
    }

    private fun OfferDecision.toOverlayStatus(): OverlayStatus = when (this) {
        OfferDecision.ACCEPT -> OverlayStatus.ACCEPT
        OfferDecision.ANALYZE -> OverlayStatus.ANALYZE
        OfferDecision.REJECT -> OverlayStatus.REJECT
    }

    private fun Long.formatBrl(): String = NumberFormat.getCurrencyInstance(BRAZIL).format(this / 100.0)
    private fun Long.formatRating(): String = "%.2f".format(BRAZIL, this / 100.0)
    private fun Long.formatDuration(): String = "${(this + 59) / 60} min"
    private fun Long.formatDistance(): String = "%.1f km".format(BRAZIL, this / 1_000.0)

    private fun <T> br.com.nexo.driver.offer.Confidence<T>.isUsable() = value != null && score >= 0.85f

    private companion object {
        val BRAZIL = Locale.forLanguageTag("pt-BR")
        val PICKUP_METRICS = setOf(Metric.PICKUP_DURATION, Metric.PICKUP_DISTANCE)
    }
}
