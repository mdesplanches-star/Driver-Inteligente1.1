package br.com.nexo.driver.journey

import br.com.nexo.driver.evaluation.OfferDecision
import br.com.nexo.driver.offer.NormalizedOffer
import br.com.nexo.driver.offer.OfferSource
import java.util.Locale
import java.util.UUID

enum class RideHistoryDecision { ACCEPT, ANALYZE, REJECT }

enum class RideHistoryStatus { ANALYZED, ACCEPTED, REJECTED, IN_RIDE, COMPLETED }

data class RideHistoryEntry(
    val id: String,
    val detectedAtEpochMs: Long,
    val decision: RideHistoryDecision,
    val source: OfferSource,
    val grossCents: Long?,
    val totalDistanceMeters: Long?,
    val totalDurationSeconds: Long?,
    val pickupAddress: String?,
    val dropoffAddress: String?,
    val estimatedRealProfitCents: Long?,
    val status: RideHistoryStatus,
    val autoDecision: AutoDecisionAction? = null,
    val autoDecisionMode: AutoDecisionMode? = null,
) {
    init {
        require(id.isNotBlank()) { "Ride history id cannot be blank." }
        require(detectedAtEpochMs > 0) { "Detected time must be positive." }
        require(grossCents == null || grossCents >= 0) { "Gross value cannot be negative." }
        require(totalDistanceMeters == null || totalDistanceMeters >= 0) { "Distance cannot be negative." }
        require(totalDurationSeconds == null || totalDurationSeconds >= 0) { "Duration cannot be negative." }
    }
}

data class RideHistorySnapshot(
    val enabled: Boolean = false,
    val entries: List<RideHistoryEntry> = emptyList(),
)

data class DailyDriverCostSettings(
    val fuelEfficiencyKmPerLiter: Double = 10.0,
    val fuelPriceCentsPerLiter: Long = 600,
    val maintenanceCents: Long = 0,
    val tireCents: Long = 0,
    val washCents: Long = 0,
    val platformFeeCents: Long = 0,
    val otherCents: Long = 0,
) {
    init {
        require(fuelEfficiencyKmPerLiter > 0.0 && fuelEfficiencyKmPerLiter.isFinite()) {
            "Fuel efficiency must be positive."
        }
        listOf(fuelPriceCentsPerLiter, maintenanceCents, tireCents, washCents, platformFeeCents, otherCents)
            .forEach { value -> require(value >= 0) { "Costs cannot be negative." } }
    }

    val fixedExtraCents: Long
        get() = maintenanceCents + tireCents + washCents + platformFeeCents + otherCents
}

data class RideSessionClockState(
    val onlineStartedAtEpochMs: Long? = null,
    val accumulatedOnlineMillis: Long = 0,
    val rideStartedAtEpochMs: Long? = null,
    val accumulatedRideMillis: Long = 0,
) {
    init {
        require(accumulatedOnlineMillis >= 0) { "Online time cannot be negative." }
        require(accumulatedRideMillis >= 0) { "Ride time cannot be negative." }
    }

    fun withReaderActive(active: Boolean, nowEpochMs: Long): RideSessionClockState = when {
        active && onlineStartedAtEpochMs == null -> copy(onlineStartedAtEpochMs = nowEpochMs)
        !active && onlineStartedAtEpochMs != null -> copy(
            onlineStartedAtEpochMs = null,
            accumulatedOnlineMillis = accumulatedOnlineMillis + (nowEpochMs - onlineStartedAtEpochMs).coerceAtLeast(0),
        )
        else -> this
    }

    fun withRideActive(active: Boolean, nowEpochMs: Long): RideSessionClockState = when {
        active && rideStartedAtEpochMs == null -> copy(rideStartedAtEpochMs = nowEpochMs)
        !active && rideStartedAtEpochMs != null -> copy(
            rideStartedAtEpochMs = null,
            accumulatedRideMillis = accumulatedRideMillis + (nowEpochMs - rideStartedAtEpochMs).coerceAtLeast(0),
        )
        else -> this
    }

    fun onlineMillis(nowEpochMs: Long): Long =
        accumulatedOnlineMillis + onlineStartedAtEpochMs.deltaUntil(nowEpochMs)

    fun rideMillis(nowEpochMs: Long): Long =
        accumulatedRideMillis + rideStartedAtEpochMs.deltaUntil(nowEpochMs)

    private fun Long?.deltaUntil(nowEpochMs: Long): Long =
        this?.let { (nowEpochMs - it).coerceAtLeast(0) } ?: 0L
}

data class DailyDriverSummary(
    val distanceMeters: Double,
    val onlineMillis: Long,
    val rideMillis: Long,
    val grossProfitCents: Long,
    val realProfitCents: Long,
    val fuelCostCents: Long,
    val extraCostCents: Long,
    val analyzedCount: Int,
    val acceptedCount: Int,
    val rejectedCount: Int,
)

class DailyDriverSummaryCalculator {
    fun fuelCostCents(distanceMeters: Double, settings: DailyDriverCostSettings): Long {
        val kilometres = (distanceMeters / 1_000.0).coerceAtLeast(0.0)
        return ((kilometres / settings.fuelEfficiencyKmPerLiter) * settings.fuelPriceCentsPerLiter).toLong()
    }

    fun realProfitCents(grossCents: Long, distanceMeters: Double, settings: DailyDriverCostSettings): Long =
        grossCents - fuelCostCents(distanceMeters, settings) - settings.fixedExtraCents

    fun summarize(
        distanceMeters: Double,
        clock: RideSessionClockState,
        history: List<RideHistoryEntry>,
        settings: DailyDriverCostSettings,
        nowEpochMs: Long,
    ): DailyDriverSummary {
        val accepted = history.filter { it.status in setOf(RideHistoryStatus.ACCEPTED, RideHistoryStatus.IN_RIDE, RideHistoryStatus.COMPLETED) }
        val gross = accepted.sumOf { it.grossCents ?: 0L }
        val fuel = fuelCostCents(distanceMeters, settings)
        return DailyDriverSummary(
            distanceMeters = distanceMeters,
            onlineMillis = clock.onlineMillis(nowEpochMs),
            rideMillis = clock.rideMillis(nowEpochMs),
            grossProfitCents = gross,
            realProfitCents = gross - fuel - settings.fixedExtraCents,
            fuelCostCents = fuel,
            extraCostCents = settings.fixedExtraCents,
            analyzedCount = history.count { it.status == RideHistoryStatus.ANALYZED },
            acceptedCount = history.count { it.status in setOf(RideHistoryStatus.ACCEPTED, RideHistoryStatus.IN_RIDE, RideHistoryStatus.COMPLETED) },
            rejectedCount = history.count { it.status == RideHistoryStatus.REJECTED },
        )
    }
}

fun NormalizedOffer.toRideHistoryEntry(
    decision: OfferDecision,
    totalDistanceMeters: Long?,
    totalDurationSeconds: Long?,
    estimatedRealProfitCents: Long?,
    nowEpochMs: Long = detectedAtEpochMs,
): RideHistoryEntry = RideHistoryEntry(
    id = UUID.nameUUIDFromBytes(
        listOf(source.name, kind.name, detectedAtEpochMs, payout.value?.cents, totalDistanceMeters, totalDurationSeconds)
            .joinToString("|")
            .toByteArray(),
    ).toString(),
    detectedAtEpochMs = nowEpochMs,
    decision = decision.toRideHistoryDecision(),
    source = source,
    grossCents = payout.value?.cents,
    totalDistanceMeters = totalDistanceMeters,
    totalDurationSeconds = totalDurationSeconds,
    pickupAddress = pickup.location.value?.address?.sanitizeAddress(),
    dropoffAddress = trip.location.value?.address?.sanitizeAddress(),
    estimatedRealProfitCents = estimatedRealProfitCents,
    status = decision.toRideHistoryStatus(),
)

private fun OfferDecision.toRideHistoryDecision(): RideHistoryDecision = when (this) {
    OfferDecision.ACCEPT -> RideHistoryDecision.ACCEPT
    OfferDecision.ANALYZE -> RideHistoryDecision.ANALYZE
    OfferDecision.REJECT -> RideHistoryDecision.REJECT
}

private fun OfferDecision.toRideHistoryStatus(): RideHistoryStatus = when (this) {
    OfferDecision.ACCEPT -> RideHistoryStatus.ACCEPTED
    OfferDecision.ANALYZE -> RideHistoryStatus.ANALYZED
    OfferDecision.REJECT -> RideHistoryStatus.REJECTED
}

private fun String.sanitizeAddress(): String = trim()
    .replace(Regex("\\s+"), " ")
    .take(MAX_ADDRESS_LENGTH)

fun Long.formatBrlCompact(): String =
    java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(this / 100.0)

fun Long.formatClockDuration(): String {
    val totalMinutes = this.coerceAtLeast(0) / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"
}

private const val MAX_ADDRESS_LENGTH = 140
