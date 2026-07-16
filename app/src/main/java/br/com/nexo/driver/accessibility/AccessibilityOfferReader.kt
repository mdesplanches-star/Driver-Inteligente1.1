package br.com.nexo.driver.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import br.com.nexo.driver.ocr.OcrTextBlock
import br.com.nexo.driver.ocr.OcrTextSnapshot
import br.com.nexo.driver.offer.FieldSource

/**
 * Converts the active accessibility tree into the same ordered text snapshot consumed by the
 * existing offer parser. This reader is intentionally read-only: it never performs actions,
 * gestures or clicks against another app.
 */
class AccessibilityOfferReader(
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    fun read(root: AccessibilityNodeInfo?, layoutHint: String? = null): OcrTextSnapshot? {
        if (root == null) return null
        return read(listOf(root), layoutHint)
    }

    /**
     * Uber may place its offer sheet in a separate application window while the map remains the
     * active root. Reading every provider-owned root mirrors what Android's UI inspector sees and
     * prevents the route legs from being silently omitted.
     */
    fun read(roots: List<AccessibilityNodeInfo>, layoutHint: String? = null): OcrTextSnapshot? {
        return readDetailed(roots, layoutHint).snapshot
    }

    fun readDetailed(
        roots: List<AccessibilityNodeInfo>,
        layoutHint: String? = null,
        extraText: List<String> = emptyList(),
    ): AccessibilityReadResult {
        if (roots.isEmpty()) {
            return AccessibilityReadResult(
                snapshot = null,
                diagnostics = AccessibilityReadDiagnostics(0, 0, 0, 0, 0),
                rawText = "",
            )
        }
        val lines = mutableListOf<String>()
        val budget = TraversalBudget()
        extraText.forEach { value -> budget.add(value, lines) }
        roots.forEach { root -> root.collectText(lines, budget, depth = 0) }
        val flattened = lines.flatMap(String::lines).map(String::trim).filter(String::isNotEmpty)
        val diagnostics = AccessibilityReadDiagnostics(
            rootCount = roots.size,
            textLineCount = flattened.size,
            cardAnchorCount = flattened.count(::isOfferAnchor),
            payoutTokenCount = flattened.count { PAYOUT_TOKEN.containsMatchIn(it) },
            routeLegCount = flattened.count { ROUTE_LEG_TOKEN.containsMatchIn(it) },
        )
        val cardLines = OfferCardTextRegionExtractor.extract(lines, layoutHint)
            ?: return AccessibilityReadResult(
                snapshot = null,
                diagnostics = diagnostics,
                rawText = flattened.joinToString("\n"),
            )
        val blocks = cardLines
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .mapIndexed { index, text ->
                OcrTextBlock(
                    text = text,
                    readingOrder = index,
                    confidence = ACCESSIBILITY_CONFIDENCE,
                )
            }
        return AccessibilityReadResult(
            snapshot = OcrTextSnapshot(
                blocks = blocks,
                capturedAtEpochMs = nowEpochMs(),
                layoutHint = layoutHint ?: blocks.inferLayoutHint(),
                fieldSource = FieldSource.ACCESSIBILITY,
            ),
            diagnostics = diagnostics,
            rawText = flattened.joinToString("\n"),
        )
    }

    fun collectTextFragments(root: AccessibilityNodeInfo?): List<String> {
        if (root == null) return emptyList()
        val output = mutableListOf<String>()
        root.collectText(output, TraversalBudget(), depth = 0)
        return output
    }

    private fun AccessibilityNodeInfo.collectText(
        output: MutableList<String>,
        budget: TraversalBudget,
        depth: Int,
    ) {
        if (!budget.visitNode(depth)) return
        text?.toString()?.let { value -> budget.add(value, output) }
        contentDescription?.toString()?.let { value -> budget.add(value, output) }
        if (depth >= MAX_TREE_DEPTH) return
        repeat(childCount) { index ->
            getChild(index)?.let { child ->
                try {
                    child.collectText(output, budget, depth + 1)
                } finally {
                    child.recycle()
                }
            }
        }
    }

    private fun List<OcrTextBlock>.inferLayoutHint(): String? {
        val joined = joinToString("\n") { it.text }.lowercase()
        return when {
            "pgto. no app" in joined ||
                "negocia" in joined ||
                "perfil essencial" in joined ||
                "perfil premium" in joined -> "99"
            "uberx" in joined || "trip radar" in joined -> "uber"
            else -> null
        }
    }

    private companion object {
        const val ACCESSIBILITY_CONFIDENCE = 0.96f
        const val MAX_TREE_DEPTH = 8
        const val MAX_TREE_NODES = 220
        const val MAX_COMBINED_TEXT_CHARS = 4_000
        val PAYOUT_TOKEN = Regex("(?i)r\\$\\s*[\\d.]+(?:,[\\d]{1,2})?")
        val ROUTE_LEG_TOKEN = Regex("(?i)\\d+\\s*(?:min|minutos)\\s*(?:\\(\\s*)?[\\d.,]+\\s*km")
    }

    private class TraversalBudget {
        private var nodes = 0
        private var characters = 0

        fun visitNode(depth: Int): Boolean {
            if (depth > MAX_TREE_DEPTH || nodes >= MAX_TREE_NODES || characters >= MAX_COMBINED_TEXT_CHARS) {
                return false
            }
            nodes += 1
            return true
        }

        fun add(value: String, output: MutableList<String>) {
            val normalized = value.trim()
            if (normalized.isEmpty() || characters >= MAX_COMBINED_TEXT_CHARS) return
            val remaining = MAX_COMBINED_TEXT_CHARS - characters
            val bounded = normalized.take(remaining)
            if (bounded.isNotEmpty()) {
                output += bounded
                characters += bounded.length
            }
        }
    }
}

private fun isOfferAnchor(line: String): Boolean =
    line.matches(Regex("(?i)\\s*(?:\\d+\\s*)?(uber\\s*x|uber black|uber comfort|uber flash|uber\\s*xl).*")) ||
        line.contains("pgto. no app", ignoreCase = true) ||
        line.contains("pagamento no app", ignoreCase = true) ||
        line.contains("negocia", ignoreCase = true)

data class AccessibilityReadResult(
    val snapshot: OcrTextSnapshot?,
    val diagnostics: AccessibilityReadDiagnostics,
    val rawText: String,
)

data class AccessibilityReadDiagnostics(
    val rootCount: Int,
    val textLineCount: Int,
    val cardAnchorCount: Int,
    val payoutTokenCount: Int,
    val routeLegCount: Int,
)

/** Identifies the provider from the event owner even when a Compose card omits its brand label. */
fun accessibilityLayoutHint(packageName: CharSequence?): String? {
    val normalized = packageName?.toString()?.lowercase().orEmpty()
    return when {
        normalized == "com.ubercab.driver" || normalized.startsWith("com.ubercab.driver.") -> "uber"
        normalized.contains("taxis99") ||
            normalized.contains("99driver") ||
            normalized.contains("didiglobal.driver") -> "99"
        else -> null
    }
}
