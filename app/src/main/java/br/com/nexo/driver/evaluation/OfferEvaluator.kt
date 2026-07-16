package br.com.nexo.driver.evaluation

import br.com.nexo.driver.offer.Confidence
import br.com.nexo.driver.offer.DerivedMetrics
import br.com.nexo.driver.offer.Distance
import br.com.nexo.driver.offer.Duration
import br.com.nexo.driver.offer.FieldSource
import br.com.nexo.driver.offer.NormalizedOffer

private const val DEFAULT_CONFIDENCE_THRESHOLD = 0.85f

enum class Metric {
    PAYOUT,
    RATE_PER_KM,
    RATE_PER_HOUR,
    PICKUP_DISTANCE,
    PICKUP_DURATION,
    TRIP_DISTANCE,
    TRIP_DURATION,
    TOTAL_DISTANCE,
    TOTAL_DURATION,
    PASSENGER_RATING,
    HAS_MULTIPLE_STOPS,
    IS_LONG_TRIP,
    IS_TOWARD_DESTINATION,
    ENDS_NEAR_HOME,
}

enum class Comparator { AT_LEAST, AT_MOST, IS_TRUE, IS_FALSE }

enum class EvaluationMode { SCORE, ELIMINATORY }

enum class MetricStatus { PASS, NEAR, FAIL, UNKNOWN }

enum class OfferDecision { ACCEPT, ANALYZE, REJECT }

data class FilterRule(
    val metric: Metric,
    val comparator: Comparator,
    val target: Long? = null,
    val tolerancePercent: Int = 10,
    val weight: Int = 1,
    val mode: EvaluationMode = EvaluationMode.SCORE,
    val enabled: Boolean = true,
) {
    init {
        require(tolerancePercent in 0..100)
        require(weight > 0)
        if (comparator == Comparator.AT_LEAST || comparator == Comparator.AT_MOST) {
            require(target != null) { "Numeric rules require a target." }
        }
    }
}

data class MetricEvaluation(
    val rule: FilterRule,
    val observedValue: Long?,
    val confidence: Float,
    val status: MetricStatus,
    val score: Int,
)

data class EvaluationResult(
    val metrics: List<MetricEvaluation>,
    val weightedScore: Int,
    val decision: OfferDecision,
) {
    val hasIncompleteData: Boolean get() = metrics.any { it.status == MetricStatus.UNKNOWN }
}

class OfferEvaluator(
    private val confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD,
    private val acceptThreshold: Int = 80,
    private val analyzeThreshold: Int = 50,
) {
    init {
        require(confidenceThreshold in 0f..1f)
        require(acceptThreshold in analyzeThreshold..100)
    }

    fun derive(offer: NormalizedOffer): DerivedMetrics {
        val totalDistance = combineDistance(offer.pickup.distance, offer.trip.distance)
        val totalDuration = combineDuration(offer.pickup.duration, offer.trip.duration)
        val payout = offer.payout
        val totalMeters = totalDistance.value?.meters
        val totalSeconds = totalDuration.value?.seconds
        val rateConfidence = minOf(payout.score, totalDistance.score)
        val hourConfidence = minOf(payout.score, totalDuration.score)
        val ratePerKm = if (payout.value != null && (totalMeters ?: 0) > 0) {
            payout.value.cents * 1_000 / requireNotNull(totalMeters)
        } else {
            null
        }
        val ratePerHour = if (payout.value != null && (totalSeconds ?: 0) > 0) {
            payout.value.cents * 3_600 / requireNotNull(totalSeconds)
        } else {
            null
        }
        return DerivedMetrics(
            totalDistance = totalDistance,
            totalDuration = totalDuration,
            ratePerKm = Confidence(ratePerKm, rateConfidence, FieldSource.DERIVED),
            ratePerHour = Confidence(ratePerHour, hourConfidence, FieldSource.DERIVED),
        )
    }

    fun evaluate(offer: NormalizedOffer, rules: List<FilterRule>): EvaluationResult {
        val derived = derive(offer)
        val activeRules = rules.filter { it.enabled }
        if (activeRules.isEmpty()) {
            return EvaluationResult(emptyList(), weightedScore = 0, decision = OfferDecision.ANALYZE)
        }
        val metrics = activeRules.map { rule -> evaluateMetric(rule, valueFor(rule.metric, offer, derived)) }
        val totalWeight = metrics.sumOf { it.rule.weight }.coerceAtLeast(1)
        val weightedScore = metrics.sumOf { it.score * it.rule.weight } / totalWeight
        val hardFailure = metrics.any { it.rule.mode == EvaluationMode.ELIMINATORY && it.status == MetricStatus.FAIL }
        val hardUnknown = metrics.any { it.rule.mode == EvaluationMode.ELIMINATORY && it.status == MetricStatus.UNKNOWN }
        val decision = when {
            hardFailure -> OfferDecision.REJECT
            hardUnknown -> OfferDecision.ANALYZE
            weightedScore >= acceptThreshold && metrics.none { it.status == MetricStatus.UNKNOWN } -> OfferDecision.ACCEPT
            weightedScore >= analyzeThreshold -> OfferDecision.ANALYZE
            else -> OfferDecision.REJECT
        }
        return EvaluationResult(metrics, weightedScore, decision)
    }

    private fun evaluateMetric(rule: FilterRule, metric: Confidence<Long>): MetricEvaluation {
        if (!metric.isUsable(confidenceThreshold)) {
            return MetricEvaluation(rule, metric.value, metric.score, MetricStatus.UNKNOWN, 50)
        }
        val value = requireNotNull(metric.value)
        val status = when (rule.comparator) {
            Comparator.AT_LEAST -> numericAtLeast(value, requireNotNull(rule.target), rule.tolerancePercent)
            Comparator.AT_MOST -> numericAtMost(value, requireNotNull(rule.target), rule.tolerancePercent)
            Comparator.IS_TRUE -> if (value == 1L) MetricStatus.PASS else MetricStatus.FAIL
            Comparator.IS_FALSE -> if (value == 0L) MetricStatus.PASS else MetricStatus.FAIL
        }
        return MetricEvaluation(rule, value, metric.score, status, status.toScore())
    }

    private fun valueFor(metric: Metric, offer: NormalizedOffer, derived: DerivedMetrics): Confidence<Long> = when (metric) {
        Metric.PAYOUT -> offer.payout.map { it.cents }
        Metric.RATE_PER_KM -> derived.ratePerKm
        Metric.RATE_PER_HOUR -> derived.ratePerHour
        Metric.PICKUP_DISTANCE -> offer.pickup.distance.map { it.meters }
        Metric.PICKUP_DURATION -> offer.pickup.duration.map { it.seconds }
        Metric.TRIP_DISTANCE -> offer.trip.distance.map { it.meters }
        Metric.TRIP_DURATION -> offer.trip.duration.map { it.seconds }
        Metric.TOTAL_DISTANCE -> derived.totalDistance.map { it.meters }
        Metric.TOTAL_DURATION -> derived.totalDuration.map { it.seconds }
        Metric.PASSENGER_RATING -> offer.passenger.rating
        Metric.HAS_MULTIPLE_STOPS -> offer.stopCount.map { if (it > 1) 1L else 0L }
        Metric.IS_LONG_TRIP -> offer.longTripHint.map { if (it) 1L else 0L }
        Metric.IS_TOWARD_DESTINATION -> offer.destinationDirectionHint.map { if (it) 1L else 0L }
        Metric.ENDS_NEAR_HOME -> offer.endsNearHome.map { if (it) 1L else 0L }
    }

    private fun numericAtLeast(value: Long, target: Long, tolerance: Int): MetricStatus = when {
        value >= target -> MetricStatus.PASS
        value >= target * (100 - tolerance) / 100 -> MetricStatus.NEAR
        else -> MetricStatus.FAIL
    }

    private fun numericAtMost(value: Long, target: Long, tolerance: Int): MetricStatus = when {
        value <= target -> MetricStatus.PASS
        value <= target * (100 + tolerance) / 100 -> MetricStatus.NEAR
        else -> MetricStatus.FAIL
    }

    private fun combineDistance(first: Confidence<Distance>, second: Confidence<Distance>): Confidence<Distance> =
        Confidence(
            value = if (first.value != null && second.value != null) Distance(first.value.meters + second.value.meters) else null,
            score = minOf(first.score, second.score),
            source = FieldSource.DERIVED,
        )

    private fun combineDuration(first: Confidence<Duration>, second: Confidence<Duration>): Confidence<Duration> =
        Confidence(
            value = if (first.value != null && second.value != null) Duration(first.value.seconds + second.value.seconds) else null,
            score = minOf(first.score, second.score),
            source = FieldSource.DERIVED,
        )
}

private fun MetricStatus.toScore() = when (this) {
    MetricStatus.PASS -> 100
    MetricStatus.NEAR, MetricStatus.UNKNOWN -> 50
    MetricStatus.FAIL -> 0
}

private fun <T, R> Confidence<T>.map(transform: (T) -> R): Confidence<R> =
    Confidence(value?.let(transform), score, source)
