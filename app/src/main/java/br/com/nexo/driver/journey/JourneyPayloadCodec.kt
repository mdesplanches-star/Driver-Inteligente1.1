package br.com.nexo.driver.journey

import br.com.nexo.driver.offer.OfferSource
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

internal object RideHistoryPayloadCodec {
    private const val SCHEMA = "driver-ride-history-v2"
    private const val LEGACY_SCHEMA = "driver-ride-history-v1"
    private const val ENTRY = "e"

    fun encode(entries: List<RideHistoryEntry>): String = buildString {
        append(SCHEMA)
        entries.forEach { entry ->
            append('\n')
            append(ENTRY).append('\t')
            append(text(entry.id)).append('\t')
            append(entry.detectedAtEpochMs).append('\t')
            append(entry.decision.name).append('\t')
            append(entry.source.name).append('\t')
            append(entry.grossCents ?: "").append('\t')
            append(entry.totalDistanceMeters ?: "").append('\t')
            append(entry.totalDurationSeconds ?: "").append('\t')
            append(text(entry.pickupAddress.orEmpty())).append('\t')
            append(text(entry.dropoffAddress.orEmpty())).append('\t')
            append(entry.estimatedRealProfitCents ?: "").append('\t')
            append(entry.status.name).append('\t')
            append(entry.autoDecision?.name ?: "").append('\t')
            append(entry.autoDecisionMode?.name ?: "")
        }
    }

    fun decode(payload: String?): List<RideHistoryEntry> {
        if (payload.isNullOrBlank()) return emptyList()
        val lines = payload.lineSequence().iterator()
        if (!lines.hasNext()) return emptyList()
        val schema = lines.next()
        if (schema !in setOf(SCHEMA, LEGACY_SCHEMA)) return emptyList()
        return buildList {
            while (lines.hasNext()) {
                parseEntry(lines.next(), schema)?.let(::add)
            }
        }.sortedByDescending { it.detectedAtEpochMs }
    }

    private fun parseEntry(line: String, schema: String): RideHistoryEntry? = runCatching {
        val parts = line.split('\t')
        val expectedSize = if (schema == LEGACY_SCHEMA) 12 else 14
        require(parts.size == expectedSize && parts[0] == ENTRY)
        RideHistoryEntry(
            id = untext(parts[1]),
            detectedAtEpochMs = parts[2].toLong(),
            decision = RideHistoryDecision.valueOf(parts[3]),
            source = OfferSource.valueOf(parts[4]),
            grossCents = parts[5].toLongOrNull(),
            totalDistanceMeters = parts[6].toLongOrNull(),
            totalDurationSeconds = parts[7].toLongOrNull(),
            pickupAddress = untext(parts[8]).ifBlank { null },
            dropoffAddress = untext(parts[9]).ifBlank { null },
            estimatedRealProfitCents = parts[10].toLongOrNull(),
            status = RideHistoryStatus.valueOf(parts[11]),
            autoDecision = parts.getOrNull(12)?.takeIf { it.isNotBlank() }?.let(AutoDecisionAction::valueOf),
            autoDecisionMode = parts.getOrNull(13)?.takeIf { it.isNotBlank() }?.let(AutoDecisionMode::valueOf),
        )
    }.getOrNull()

    private fun text(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(UTF_8))

    private fun untext(value: String): String = String(Base64.getUrlDecoder().decode(value), UTF_8)
}

internal object AutoDecisionSettingsCodec {
    private const val SCHEMA = "driver-auto-decision-v2"
    private const val LEGACY_SCHEMA = "driver-auto-decision-v1"

    fun encode(settings: AutoDecisionSettings): String = listOf(
        SCHEMA,
        settings.mode.name,
        settings.minimumRealProfitCents,
        settings.autoAcceptEnabled,
        settings.autoRejectEnabled,
    ).joinToString("\t")

    fun decode(payload: String?): AutoDecisionSettings = runCatching {
        val parts = payload.orEmpty().split('\t')
        require(parts[0] in setOf(SCHEMA, LEGACY_SCHEMA))
        require((parts[0] == LEGACY_SCHEMA && parts.size == 3) || (parts[0] == SCHEMA && parts.size == 5))
        AutoDecisionSettings(
            mode = AutoDecisionMode.valueOf(parts[1]),
            minimumRealProfitCents = parts[2].toLong(),
            autoAcceptEnabled = parts.getOrNull(3)?.toBooleanStrictOrNull() ?: false,
            autoRejectEnabled = parts.getOrNull(4)?.toBooleanStrictOrNull() ?: false,
        )
    }.getOrDefault(AutoDecisionSettings())
}

internal object DriverGoalSettingsCodec {
    private const val SCHEMA = "driver-goal-settings-v1"

    fun encode(settings: DriverGoalSettings): String = listOf(
        SCHEMA,
        settings.realProfitTargetCents,
        settings.grossProfitTargetCents,
        settings.distanceTargetMeters,
        settings.onlineTargetMillis,
    ).joinToString("\t")

    fun decode(payload: String?): DriverGoalSettings = runCatching {
        val parts = payload.orEmpty().split('\t')
        require(parts.size == 5 && parts[0] == SCHEMA)
        DriverGoalSettings(
            realProfitTargetCents = parts[1].toLong(),
            grossProfitTargetCents = parts[2].toLong(),
            distanceTargetMeters = parts[3].toDouble(),
            onlineTargetMillis = parts[4].toLong(),
        )
    }.getOrDefault(DriverGoalSettings())
}

internal object DailyDriverCostSettingsCodec {
    private const val SCHEMA = "driver-cost-settings-v1"

    fun encode(settings: DailyDriverCostSettings): String = listOf(
        SCHEMA,
        settings.fuelEfficiencyKmPerLiter,
        settings.fuelPriceCentsPerLiter,
        settings.maintenanceCents,
        settings.tireCents,
        settings.washCents,
        settings.platformFeeCents,
        settings.otherCents,
    ).joinToString("\t")

    fun decode(payload: String?): DailyDriverCostSettings = runCatching {
        val parts = payload.orEmpty().split('\t')
        require(parts.size == 8 && parts[0] == SCHEMA)
        DailyDriverCostSettings(
            fuelEfficiencyKmPerLiter = parts[1].toDouble(),
            fuelPriceCentsPerLiter = parts[2].toLong(),
            maintenanceCents = parts[3].toLong(),
            tireCents = parts[4].toLong(),
            washCents = parts[5].toLong(),
            platformFeeCents = parts[6].toLong(),
            otherCents = parts[7].toLong(),
        )
    }.getOrDefault(DailyDriverCostSettings())
}

internal object RideSessionClockCodec {
    private const val SCHEMA = "driver-session-clock-v1"

    fun encode(state: RideSessionClockState): String = listOf(
        SCHEMA,
        state.onlineStartedAtEpochMs ?: "",
        state.accumulatedOnlineMillis,
        state.rideStartedAtEpochMs ?: "",
        state.accumulatedRideMillis,
    ).joinToString("\t")

    fun decode(payload: String?): RideSessionClockState = runCatching {
        val parts = payload.orEmpty().split('\t')
        require(parts.size == 5 && parts[0] == SCHEMA)
        RideSessionClockState(
            onlineStartedAtEpochMs = parts[1].toLongOrNull(),
            accumulatedOnlineMillis = parts[2].toLong(),
            rideStartedAtEpochMs = parts[3].toLongOrNull(),
            accumulatedRideMillis = parts[4].toLong(),
        )
    }.getOrDefault(RideSessionClockState())
}
