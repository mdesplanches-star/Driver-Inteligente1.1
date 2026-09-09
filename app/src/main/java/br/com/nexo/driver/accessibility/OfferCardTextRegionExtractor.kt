package br.com.nexo.driver.accessibility

/** Selects only the offer card text and discards map labels, balances and unrelated controls. */
internal object OfferCardTextRegionExtractor {
    fun extract(rawLines: List<String>, layoutHint: String?): List<String>? {
        val lines = rawLines
            .flatMap { value -> value.lines() }
            .map(String::trim)
            .filter(String::isNotEmpty)
        if (lines.isEmpty()) return null

        val normalizedHint = layoutHint?.lowercase()
        val explicitCandidates = when (normalizedHint) {
            "uber" -> lines.indices.filter { index -> isUberService(lines[index]) || isUberContext(lines[index]) }
            "99" -> lines.indices.filter { index -> isNinetyNineAnchor(lines[index]) }
            else -> lines.indices.filter { index ->
                isUberService(lines[index]) || isUberContext(lines[index]) || isNinetyNineAnchor(lines[index])
            }
        }

        // Current Uber cards are server-driven and may omit a visible "Uber X/Comfort" label from
        // the accessibility tree. When the owning package already identifies the provider, a payout
        // followed by two route legs is a stronger card signal than requiring a brand string.
        val structuralCandidates = if (normalizedHint in setOf("uber", "99")) {
            lines.indices.filter { index -> hasPayout(lines[index]) }
        } else {
            emptyList()
        }

        return (explicitCandidates + structuralCandidates)
            .distinct()
            .map { start -> cardWindow(lines, start) }
            .filter(::isCompleteOfferCard)
            .maxByOrNull(::cardScore)
    }

    private fun cardWindow(lines: List<String>, start: Int): List<String> {
        // Include a small prefix when payout itself is used as the structural anchor; useful labels
        // such as Comfort/99Plus can appear immediately before the total.
        val from = (start - CARD_PREFIX_LINES).coerceAtLeast(0)
        val bounded = lines.drop(from).take(MAX_CARD_LINES)
        val actionIndex = bounded.indexOfFirst(::isTerminalAction)
        return if (actionIndex >= 0) bounded.take(actionIndex + 1) else bounded
    }

    private fun isCompleteOfferCard(lines: List<String>): Boolean =
        lines.any(::hasPayout) && lines.count(::hasRouteLeg) >= REQUIRED_ROUTE_LEGS

    private fun cardScore(lines: List<String>): Int =
        lines.count(::hasRouteLeg) * 10 +
            lines.count(::hasPayout) * 4 +
            lines.count(::hasRatingSignal) * 2 +
            lines.count(::isTerminalAction) +
            lines.count { isUberService(it) || isNinetyNineAnchor(it) } * 3

    private fun isUberService(line: String): Boolean = line.matches(
        Regex("(?i)\\s*(?:\\d+\\s*)?(uber\\s*x|uber black|(?:uber\\s+)?comfort|uber flash|uber\\s*xl).*"),
    )

    private fun isUberContext(line: String): Boolean =
        line.contains("trip radar", ignoreCase = true) ||
            line.contains("exclusive", ignoreCase = true)

    private fun isNinetyNineAnchor(line: String): Boolean =
        line.contains("pgto. no app", ignoreCase = true) ||
            line.contains("pagamento no app", ignoreCase = true) ||
            line.contains("negocia", ignoreCase = true) ||
            line.matches(Regex("(?i)\\s*(99pop|99plus).*"))

    private fun hasPayout(line: String): Boolean =
        PAYOUT_REGEX.containsMatchIn(line) && !line.contains("/km", ignoreCase = true)

    private fun hasRouteLeg(line: String): Boolean = ROUTE_LEG_REGEX.containsMatchIn(line)

    private fun hasRatingSignal(line: String): Boolean =
        line.contains("corridas", ignoreCase = true) ||
            line.contains("estrela", ignoreCase = true) ||
            RATING_REGEX.containsMatchIn(line)

    private fun isTerminalAction(line: String): Boolean =
        line.contains("selecionar", ignoreCase = true) ||
            line.contains("aceitar por", ignoreCase = true) ||
            line.equals("aceitar", ignoreCase = true) ||
            line.equals("decline", ignoreCase = true) ||
            line.equals("recusar", ignoreCase = true)

    private val PAYOUT_REGEX = Regex("(?i)r\\$\\s*[\\d.]+(?:,[\\d]{1,2})?")
    private val ROUTE_LEG_REGEX = Regex(
        "(?i)\\d+\\s*(?:min|minutos)\\s*(?:\\(\\s*)?[\\d.,]+\\s*km",
    )
    private val RATING_REGEX = Regex("(?i)\\b[345][,.]\\d{1,2}\\b")
    private const val REQUIRED_ROUTE_LEGS = 2
    private const val CARD_PREFIX_LINES = 2
    private const val MAX_CARD_LINES = 48
}
