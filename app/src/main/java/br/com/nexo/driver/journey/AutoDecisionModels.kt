package br.com.nexo.driver.journey

import br.com.nexo.driver.evaluation.EvaluationResult
import br.com.nexo.driver.evaluation.OfferDecision

enum class AutoDecisionMode {
    OFF,
    ASSISTIVE,
    TRAINING,
}

enum class AutoDecisionAction {
    WOULD_ACCEPT,
    WOULD_REJECT,
    ANALYZE,
}

data class AutoDecisionSettings(
    val mode: AutoDecisionMode = AutoDecisionMode.OFF,
    val minimumRealProfitCents: Long = 0,
    val autoAcceptEnabled: Boolean = false,
    val autoRejectEnabled: Boolean = false,
) {
    init {
        require(minimumRealProfitCents >= 0) { "Minimum real profit cannot be negative." }
    }
}

data class AutoDecisionResult(
    val action: AutoDecisionAction,
    val mode: AutoDecisionMode,
    val estimatedRealProfitCents: Long?,
    val goalProgressPercent: Int,
    val reason: String,
)

data class DriverGoalSettings(
    val realProfitTargetCents: Long = 25_000,
    val grossProfitTargetCents: Long = 35_000,
    val distanceTargetMeters: Double = 120_000.0,
    val onlineTargetMillis: Long = 8 * 60 * 60 * 1_000L,
) {
    init {
        require(realProfitTargetCents > 0) { "Real profit target must be positive." }
        require(grossProfitTargetCents > 0) { "Gross profit target must be positive." }
        require(distanceTargetMeters > 0.0 && distanceTargetMeters.isFinite()) { "Distance target must be positive." }
        require(onlineTargetMillis > 0) { "Online target must be positive." }
    }
}

data class DriverGoalProgress(
    val realProfitPercent: Int,
    val grossProfitPercent: Int,
    val distancePercent: Int,
    val onlinePercent: Int,
    val mixedPercent: Int,
)

data class TrainingDecisionRecord(
    val rideId: String,
    val predictedAction: AutoDecisionAction,
    val finalStatus: RideHistoryStatus?,
    val recordedAtEpochMs: Long,
)

class DriverGoalProgressCalculator {
    fun calculate(summary: DailyDriverSummary, settings: DriverGoalSettings): DriverGoalProgress {
        val real = percent(summary.realProfitCents.toDouble(), settings.realProfitTargetCents.toDouble())
        val gross = percent(summary.grossProfitCents.toDouble(), settings.grossProfitTargetCents.toDouble())
        val distance = percent(summary.distanceMeters, settings.distanceTargetMeters)
        val online = percent(summary.onlineMillis.toDouble(), settings.onlineTargetMillis.toDouble())
        return DriverGoalProgress(
            realProfitPercent = real,
            grossProfitPercent = gross,
            distancePercent = distance,
            onlinePercent = online,
            mixedPercent = ((real * 0.45) + (gross * 0.25) + (distance * 0.15) + (online * 0.15)).toInt(),
        )
    }

    private fun percent(value: Double, target: Double): Int =
        ((value.coerceAtLeast(0.0) / target) * 100).toInt().coerceIn(0, 999)
}

class AutoDecisionEngine {
    fun decide(
        settings: AutoDecisionSettings,
        evaluation: EvaluationResult,
        estimatedRealProfitCents: Long?,
        goalProgress: DriverGoalProgress,
    ): AutoDecisionResult {
        if (settings.mode == AutoDecisionMode.OFF) {
            return AutoDecisionResult(
                action = AutoDecisionAction.ANALYZE,
                mode = settings.mode,
                estimatedRealProfitCents = estimatedRealProfitCents,
                goalProgressPercent = goalProgress.mixedPercent,
                reason = "Auto-decisao desligada.",
            )
        }
        if (estimatedRealProfitCents == null) {
            return AutoDecisionResult(
                action = AutoDecisionAction.ANALYZE,
                mode = settings.mode,
                estimatedRealProfitCents = null,
                goalProgressPercent = goalProgress.mixedPercent,
                reason = "Lucro real indisponivel.",
            )
        }
        if (evaluation.decision == OfferDecision.REJECT || estimatedRealProfitCents < settings.minimumRealProfitCents) {
            if (!settings.autoRejectEnabled) {
                return AutoDecisionResult(
                    action = AutoDecisionAction.ANALYZE,
                    mode = settings.mode,
                    estimatedRealProfitCents = estimatedRealProfitCents,
                    goalProgressPercent = goalProgress.mixedPercent,
                    reason = "Auto recusar desligado.",
                )
            }
            return AutoDecisionResult(
                action = AutoDecisionAction.WOULD_REJECT,
                mode = settings.mode,
                estimatedRealProfitCents = estimatedRealProfitCents,
                goalProgressPercent = goalProgress.mixedPercent,
                reason = "Oferta abaixo dos filtros ou do lucro real minimo.",
            )
        }
        if (evaluation.decision == OfferDecision.ACCEPT) {
            if (!settings.autoAcceptEnabled) {
                return AutoDecisionResult(
                    action = AutoDecisionAction.ANALYZE,
                    mode = settings.mode,
                    estimatedRealProfitCents = estimatedRealProfitCents,
                    goalProgressPercent = goalProgress.mixedPercent,
                    reason = "Auto aceitar desligado.",
                )
            }
            return AutoDecisionResult(
                action = AutoDecisionAction.WOULD_ACCEPT,
                mode = settings.mode,
                estimatedRealProfitCents = estimatedRealProfitCents,
                goalProgressPercent = goalProgress.mixedPercent,
                reason = "Oferta passa nos filtros e no lucro real minimo.",
            )
        }
        return AutoDecisionResult(
            action = AutoDecisionAction.ANALYZE,
            mode = settings.mode,
            estimatedRealProfitCents = estimatedRealProfitCents,
            goalProgressPercent = goalProgress.mixedPercent,
            reason = "Oferta precisa de confirmacao manual.",
        )
    }
}
