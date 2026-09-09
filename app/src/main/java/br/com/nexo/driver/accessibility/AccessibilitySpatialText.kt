package br.com.nexo.driver.accessibility

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Read-only representation of one node exposed by Android's accessibility tree.
 *
 * Keeping geometry and both text channels lets us reconstruct visual rows before handing the
 * content to the existing text parser. No action/click information is used for offer interaction.
 */
internal data class AccessibilityTextFragment(
    val text: String?,
    val contentDescription: String?,
    val viewIdResourceName: String?,
    val className: String?,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val depth: Int,
    val visibleToUser: Boolean,
) {
    val centerY: Int get() = top + ((bottom - top) / 2)
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

/**
 * Turns structured accessibility nodes into parser-friendly reading order.
 *
 * Uber's current offer card is server-driven and frequently renders duration and distance in
 * separate TextViews on the same visual row. The legacy parser expects a single
 * `5 min (2,1 km)` line, so this builder reconstructs that semantic row without inventing fields.
 */
internal object AccessibilitySpatialTextBuilder {
    fun build(
        fragments: List<AccessibilityTextFragment>,
        extraText: List<String> = emptyList(),
    ): List<String> {
        if (fragments.isEmpty()) return normalizeExtra(extraText)

        val visible = fragments.filter(AccessibilityTextFragment::visibleToUser)
        val source = if (visible.isNotEmpty()) visible else fragments
        val candidates = source
            .flatMap(::nodeCandidates)
            .filter { it.value.isNotBlank() }

        val rows = mutableListOf<MutableList<TextCandidate>>()
        candidates
            .sortedWith(compareBy<TextCandidate>({ it.fragment.top }, { it.fragment.left }, { it.priority }))
            .forEach { candidate ->
                val row = rows.lastOrNull()?.takeIf { existing -> belongsToRow(candidate, existing) }
                if (row == null) rows += mutableListOf(candidate) else row += candidate
            }

        val output = mutableListOf<String>()
        rows.forEach { row ->
            val ordered = row.sortedWith(compareBy<TextCandidate>({ it.fragment.left }, { it.priority }))
            val rowValues = ordered.map(TextCandidate::value).stableDistinct()

            // A contentDescription can already describe both values; otherwise combine sibling
            // TextViews that occupy the same visual row.
            val routeLine = rowValues.firstNotNullOfOrNull(::parserFriendlyRouteLine)
                ?: parserFriendlyRouteLine(rowValues.joinToString(" "))
            routeLine?.let(output::add)

            // Preserve original visible/accessible strings too. They carry service type, payout,
            // addresses, ratings and server-provided accessibility wording used by later parsers.
            output += rowValues
        }

        output += normalizeExtra(extraText)
        return output
            .flatMap(String::lines)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .stableDistinct()
    }

    private fun nodeCandidates(fragment: AccessibilityTextFragment): List<TextCandidate> {
        val visibleText = fragment.text.normalizedOrNull()
        val accessibleText = fragment.contentDescription.normalizedOrNull()
        return buildList {
            // Accessibility-specific wording is often semantically richer, but visible text must
            // remain available because not every Uber/99 component publishes a description.
            accessibleText?.let { add(TextCandidate(fragment, it, priority = 0)) }
            if (visibleText != null && !visibleText.equals(accessibleText, ignoreCase = true)) {
                add(TextCandidate(fragment, visibleText, priority = 1))
            }
        }
    }

    private fun belongsToRow(candidate: TextCandidate, row: List<TextCandidate>): Boolean {
        val representative = row.first().fragment
        val centerTolerance = max(MIN_ROW_CENTER_TOLERANCE_PX, min(candidate.fragment.height, representative.height))
        if (abs(candidate.fragment.centerY - representative.centerY) <= centerTolerance) return true

        val overlap = min(candidate.fragment.bottom, representative.bottom) -
            max(candidate.fragment.top, representative.top)
        val minimumHeight = min(candidate.fragment.height, representative.height)
        return overlap > 0 && overlap.toFloat() / minimumHeight >= MIN_VERTICAL_OVERLAP
    }

    /**
     * Normalizes any text that actually contains both a time and a distance to the format already
     * understood by OfferTextParser. It also handles metres and hour+minute wording exposed by
     * accessibility descriptions.
     */
    private fun parserFriendlyRouteLine(value: String): String? {
        val minutes = extractMinutes(value) ?: return null
        val kilometres = extractKilometres(value) ?: return null
        return "$minutes min (${formatKilometres(kilometres)} km)"
    }

    private fun extractMinutes(value: String): Long? {
        val hourMinute = HOUR_MINUTE_REGEX.find(value)
        if (hourMinute != null) {
            val hours = hourMinute.groupValues[1].toLongOrNull() ?: return null
            val minutes = hourMinute.groupValues[2].toLongOrNull() ?: 0L
            return hours * 60L + minutes
        }
        return MINUTE_REGEX.find(value)?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun extractKilometres(value: String): Double? {
        val kilometreMatch = KILOMETRE_REGEX.find(value)
        if (kilometreMatch != null) {
            return decimalNumber(kilometreMatch.groupValues[1])
        }
        val metreMatch = METRE_REGEX.find(value) ?: return null
        val metres = decimalNumber(metreMatch.groupValues[1]) ?: return null
        return metres / 1_000.0
    }

    private fun decimalNumber(token: String): Double? {
        val compact = token.replace(" ", "")
        val normalized = if (',' in compact) {
            compact.replace(".", "").replace(',', '.')
        } else {
            compact
        }
        return normalized.toDoubleOrNull()
    }

    private fun formatKilometres(value: Double): String {
        val rendered = when {
            value < 1.0 -> "%.2f".format(java.util.Locale.US, value)
            value < 10.0 -> "%.1f".format(java.util.Locale.US, value)
            else -> "%.1f".format(java.util.Locale.US, value)
        }.trimEnd('0').trimEnd('.')
        return rendered.replace('.', ',')
    }

    private fun normalizeExtra(values: List<String>): List<String> = values
        .flatMap(String::lines)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .stableDistinct()

    private fun String?.normalizedOrNull(): String? = this
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    private fun <T> List<T>.stableDistinct(): List<T> {
        val seen = LinkedHashSet<T>()
        forEach(seen::add)
        return seen.toList()
    }

    private data class TextCandidate(
        val fragment: AccessibilityTextFragment,
        val value: String,
        val priority: Int,
    )

    private val HOUR_MINUTE_REGEX = Regex(
        "(?i)\\b(\\d+)\\s*(?:h|hora|horas)\\s*(?:(\\d+)\\s*(?:min|minuto|minutos))?",
    )
    private val MINUTE_REGEX = Regex("(?i)\\b(\\d+)\\s*(?:min|minuto|minutos)\\b")
    private val KILOMETRE_REGEX = Regex("(?i)\\b([\\d.,]+)\\s*(?:km|quilometro|quilometros|quilômetro|quilômetros)\\b")
    private val METRE_REGEX = Regex("(?i)\\b([\\d.,]+)\\s*(?:m|metro|metros)\\b")
    private const val MIN_ROW_CENTER_TOLERANCE_PX = 12
    private const val MIN_VERTICAL_OVERLAP = 0.45f
}
