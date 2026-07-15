package br.com.nexo.driver.overlay.preferences

/**
 * The values available in the four-cell overlay grid.
 *
 * Payment deliberately is not an option here: it is the single primary value
 * in the overlay header and must never be duplicated in a cell.
 */
enum class OverlayMetricField(
    val label: String,
) {
    RATE_PER_KM("R$/km"),
    RATE_PER_HOUR("R$/h"),
    PASSENGER_RATING("Nota"),
    PICKUP("Retirada"),
    TOTAL_DURATION("Duração total"),
    TOTAL_DISTANCE("Distância total"),
}

/** Fixed positions make a saved configuration stable even when the UI changes. */
enum class OverlaySlot {
    TOP_START,
    TOP_END,
    BOTTOM_START,
    BOTTOM_END,
}

/**
 * User configuration for the 2x2 metric grid.
 *
 * Exactly four different values are required, so every cell adds information
 * at a glance and the header remains the only location for the offer payment.
 */
data class OverlayPreferences(
    val fields: List<OverlayMetricField> = DEFAULT_FIELDS,
) {
    init {
        require(fields.size == OverlaySlot.entries.size) {
            "An overlay must have exactly ${OverlaySlot.entries.size} fields."
        }
        require(fields.distinct().size == fields.size) {
            "Overlay fields must be unique."
        }
    }

    operator fun get(slot: OverlaySlot): OverlayMetricField = fields[slot.ordinal]

    /** Replaces one cell while preserving the four-distinct-fields invariant. */
    fun withField(slot: OverlaySlot, field: OverlayMetricField): OverlayPreferences {
        val next = fields.toMutableList().also { it[slot.ordinal] = field }
        return OverlayPreferences(next)
    }

    companion object {
        val DEFAULT_FIELDS = listOf(
            OverlayMetricField.RATE_PER_KM,
            OverlayMetricField.RATE_PER_HOUR,
            OverlayMetricField.PASSENGER_RATING,
            OverlayMetricField.PICKUP,
        )

        val DEFAULT = OverlayPreferences(DEFAULT_FIELDS)
    }
}
