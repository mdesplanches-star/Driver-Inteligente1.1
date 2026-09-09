package br.com.nexo.driver.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import br.com.nexo.driver.ocr.OcrTextBlock
import br.com.nexo.driver.ocr.OcrTextSnapshot
import br.com.nexo.driver.offer.FieldSource

/**
 * Converts provider accessibility trees into the ordered text snapshot consumed by the existing
 * offer parser. The reader remains strictly read-only: it never performs actions, gestures or
 * clicks against another app.
 *
 * Unlike the original implementation, this reader preserves node geometry while traversing the
 * tree. Uber's server-driven cards frequently place time and distance in sibling TextViews; the
 * spatial builder reconstructs those visual rows before the text parser runs.
 */
class AccessibilityOfferReader(
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    fun read(root: AccessibilityNodeInfo?, layoutHint: String? = null): OcrTextSnapshot? {
        if (root == null) return null
        return read(listOf(root), layoutHint)
    }

    /**
     * Provider offer UI can live in a separate application window while the map remains active.
     * Reading every provider-owned root mirrors what Android's accessibility inspector sees and
     * prevents route information from being silently omitted.
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

        val fragments = mutableListOf<AccessibilityTextFragment>()
        val budget = TraversalBudget()
        roots.forEach { root -> root.collectFragments(fragments, budget, depth = 0) }

        val lines = AccessibilitySpatialTextBuilder.build(
            fragments = fragments,
            extraText = extraText,
        )
        val flattened = lines
            .flatMap(String::lines)
            .map(String::trim)
            .filter(String::isNotEmpty)

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

    /**
     * Event-source fragments are useful while a card is still settling. Keep both Android text
     * channels, but do not attempt to interpret them here.
     */
    fun collectTextFragments(root: AccessibilityNodeInfo?): List<String> {
        if (root == null) return emptyList()
        val fragments = mutableListOf<AccessibilityTextFragment>()
        root.collectFragments(fragments, TraversalBudget(), depth = 0)
        return AccessibilitySpatialTextBuilder.build(fragments)
    }

    private fun AccessibilityNodeInfo.collectFragments(
        output: MutableList<AccessibilityTextFragment>,
        budget: TraversalBudget,
        depth: Int,
    ) {
        if (!budget.visitNode(depth)) return

        val nodeText = text?.toString()?.takeBudgeted(budget)
        val nodeDescription = contentDescription?.toString()?.takeBudgeted(budget)
        if (!nodeText.isNullOrBlank() || !nodeDescription.isNullOrBlank()) {
            val bounds = Rect()
            getBoundsInScreen(bounds)
            output += AccessibilityTextFragment(
                text = nodeText,
                contentDescription = nodeDescription,
                viewIdResourceName = viewIdResourceName,
                className = className?.toString(),
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom,
                depth = depth,
                visibleToUser = isVisibleToUser,
            )
        }

        if (depth >= MAX_TREE_DEPTH) return
        repeat(childCount) { index ->
            getChild(index)?.let { child ->
                try {
                    child.collectFragments(output, budget, depth + 1)
                } finally {
                    child.recycle()
                }
            }
        }
    }

    private fun String.takeBudgeted(budget: TraversalBudget): String? {
        val normalized = trim()
        if (normalized.isEmpty()) return null
        return budget.take(normalized)
    }

    private fun List<OcrTextBlock>.inferLayoutHint(): String? {
        val joined = joinToString("\n") { it.text }.lowercase()
        return when {
            "pgto. no app" in joined ||
                "negocia" in joined ||
                "perfil essencial" in joined ||
                "perfil premium" in joined -> "99"
            "uberx" in joined ||
                "uber x" in joined ||
                "uber comfort" in joined ||
                "comfort" in joined ||
                "trip radar" in joined -> "uber"
            else -> null
        }
    }

    private companion object {
        const val ACCESSIBILITY_CONFIDENCE = 0.98f
        const val MAX_TREE_DEPTH = 14
        const val MAX_TREE_NODES = 600
        const val MAX_COMBINED_TEXT_CHARS = 8_000
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

        fun take(value: String): String? {
            if (characters >= MAX_COMBINED_TEXT_CHARS) return null
            val remaining = MAX_COMBINED_TEXT_CHARS - characters
            val bounded = value.take(remaining)
            if (bounded.isEmpty()) return null
            characters += bounded.length
            return bounded
        }
    }
}

private fun isOfferAnchor(line: String): Boolean =
    line.matches(
        Regex(
            "(?i)\\s*(?:\\d+\\s*)?(uber\\s*x|uber black|(?:uber\\s+)?comfort|uber flash|uber\\s*xl).*",
        ),
    ) ||
        line.contains("trip radar", ignoreCase = true) ||
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

/** Identifies the provider from the event owner even when a server-driven card omits its brand. */
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
