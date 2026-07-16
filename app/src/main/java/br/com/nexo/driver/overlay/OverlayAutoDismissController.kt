package br.com.nexo.driver.overlay

/**
 * Owns the short lifetime of a visible offer card. A newer offer always receives a full display
 * interval; a stale delayed callback can never dismiss it. The Android window supplies a
 * main-thread scheduler, while this small class keeps the behaviour deterministic in unit tests.
 */
internal class OverlayAutoDismissController(
    private val timeoutMs: Long = MAX_VISIBLE_DURATION_MS,
    private val postDelayed: (delayMs: Long, action: () -> Unit) -> Unit,
    private val cancelPending: () -> Unit,
    private val onTimeout: () -> Unit,
) {
    init {
        require(timeoutMs in 1..MAX_VISIBLE_DURATION_MS) {
            "Overlay auto-dismiss must occur within $MAX_VISIBLE_DURATION_MS ms."
        }
    }

    private var generation = 0L

    fun restart() {
        cancelPending()
        val scheduledGeneration = ++generation
        postDelayed(timeoutMs) {
            if (scheduledGeneration == generation) onTimeout()
        }
    }

    fun cancel() {
        generation++
        cancelPending()
    }

    internal companion object {
        const val MAX_VISIBLE_DURATION_MS = 8_000L
    }
}
