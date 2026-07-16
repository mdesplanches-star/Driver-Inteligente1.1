package br.com.nexo.driver.accessibility

/** Selects only the offer card text and discards map labels, balances and unrelated controls. */
internal object OfferCardTextRegionExtractor {
    fun extract(rawLines: List<String>, layoutHint: String?): List<String>? {
        val lines = rawLines
            .flatMap { value -> value.lines() }
            .map(String::trim)
            .filter(String::isNotEmpty)
        if (lines.isEmpty()) return null

        val candidates = when (layoutHint?.lowercase()) {
            "uber" -> lines.indices.filter { index -> isUberService(lines[index]) }
            "99" -> lines.indices.filter { index -> isNinetyNineAnchor(lines[index]) }
            else -> lines.indices.filter { index ->
                isUberService(lines[index]) || isNinetyNineAnchor(lines[index])
            }
        }
        return candidates
            .map { start -> cardWindow(lines, start) }
            .filter(::isCompleteOfferCard)
            .maxByOrNull(::cardScore)
    }

    private fun cardWindow(lines: List<String>, start: Int): List<String> {
        val bounded = lines.drop(start).take(MAX_CARD_LINES)
        val actionIndex = bounded.indexOfFirst(::isTerminalAction)
        return if (actionIndex >= 0) bounded.take(actionIndex + 1) else bounded
    }

    private fun isCompleteOfferCard(lines: List<String>): Boolean =
        lines.any(::hasPayout) && lines.count(::hasRouteLeg) >= REQUIRED_ROUTE_LEGS

    private fun cardScore(lines: List<String>): Int =
        lines.count(::hasRouteLeg) * 10 +
            lines.count(::hasPayout) * 4 +
            lines.count(::hasRatingSignal) * 2 +
            lines.count(::isTerminalAction)

    private fun isUberService(line: String): Boolean = line.matches(
        Regex("(?i)\\s*(?:\\d+\\s*)?(uber\\s*x|uber black|uber comfort|uber flash|uber\\s*xl).*"),
    )

    private fun isNinetyNineAnchor(line: String): Boolean =
        line.contains("pgto. no app", ignoreCase = true) ||
            line.contains("pagamento no app", ignoreCase = true) ||
            line.contains("negocia", ignoreCase = true)

    private fun hasPayout(line: String): Boolean =
        PAYOUT_REGEX.containsMatchIn(line) && !line.contains("/km", ignoreCase = true)

    private fun hasRouteLeg(line: String): Boolean = ROUTE_LEG_REGEX.containsMatchIn(line)

    private fun hasRatingSignal(line: String): Boolean =
        line.contains("corridas", ignoreCase = true) || RATING_REGEX.containsMatchIn(line)

    private fun isTerminalAction(line: String): Boolean =
        line.contains("selecionar", ignoreCase = true) ||
            line.contains("aceitar por", ignoreCase = true)

    private val PAYOUT_REGEX = Regex("(?i)r\\$\\s*[\\d.]+(?:,[\\d]{1,2})?")
    private val ROUTE_LEG_REGEX = Regex(
        "(?i)\\d+\\s*(?:min|minutos)\\s*(?:\\(\\s*)?[\\d.,]+\\s*km",
    )
    private val RATING_REGEX = Regex("(?i)\\b[345][,.]\\d{1,2}\\b")
    private const val REQUIRED_ROUTE_LEGS = 2
    private const val MAX_CARD_LINES = 40
}
