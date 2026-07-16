package br.com.nexo.driver.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import br.com.nexo.driver.BuildConfig
import br.com.nexo.driver.analysis.OfferAnalysisProcessor
import br.com.nexo.driver.analysis.ActiveOfferUpdateGate
import br.com.nexo.driver.ocr.OfferOcrPipeline
import br.com.nexo.driver.overlay.WindowManagerOfferOverlay
import br.com.nexo.driver.speech.OfferDecisionSpeaker

/**
 * Primary read-only offer reader. It inspects text exposed by the active app's accessibility tree
 * and feeds the same local parser/evaluator/overlay pipeline used by OCR fallback.
 */
class DriverAccessibilityService : AccessibilityService() {
    private val reader = AccessibilityOfferReader()
    private val pipeline = OfferOcrPipeline()
    private lateinit var processor: OfferAnalysisProcessor
    private var overlayWindow: WindowManagerOfferOverlay? = null
    private var speaker: OfferDecisionSpeaker? = null
    private var screenshotFallback: AccessibilityScreenshotFallback? = null
    private val eventHandler = Handler(Looper.getMainLooper())
    private var pendingRead: Runnable? = null
    private var lastNoCardLogAtMs = Long.MIN_VALUE
    private val activeOfferUpdateGate = ActiveOfferUpdateGate()

    override fun onServiceConnected() {
        overlayWindow = WindowManagerOfferOverlay(this)
        speaker = OfferDecisionSpeaker(this)
        processor = OfferAnalysisProcessor(
            context = this,
            speaker = speaker,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            screenshotFallback = AccessibilityScreenshotFallback(this) { offer, _ ->
                val result = processor.analyze(offer, AnalysisSource.OCR)
                if (result != null) {
                    publishInitialOffer(offer, AnalysisSource.OCR, result)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::processor.isInitialized) return
        val packageName = event?.packageName?.toString()
        if (packageName == this.packageName || packageName?.startsWith("${this.packageName}.") == true) return
        val eventHint = accessibilityLayoutHint(packageName)
        val extraText = if (eventHint != null) {
            buildList {
                event?.text.orEmpty().mapNotNullTo(this) { value -> value?.toString() }
                event?.source?.let { source ->
                    try {
                        addAll(reader.collectTextFragments(source))
                    } finally {
                        source.recycle()
                    }
                }
            }
        } else {
            emptyList()
        }
        val receivedAtNanos = System.nanoTime()
        pendingRead?.let(eventHandler::removeCallbacks)
        pendingRead = Runnable {
            processActiveWindow(packageName, receivedAtNanos, extraText)
        }.also { read ->
            eventHandler.postDelayed(read, ACCESSIBILITY_SETTLE_DELAY_MS)
        }
    }

    private fun processActiveWindow(
        packageName: String?,
        receivedAtNanos: Long,
        extraText: List<String>,
    ) {
        val startedAtNanos = System.nanoTime()
        val windowInfos = windows.toList()
        val allRoots = windowInfos
            .sortedBy { window -> window.layer }
            .mapNotNull { window -> window.root }
        val providerRoots = allRoots.filter { root ->
            accessibilityLayoutHint(root.packageName) != null
        }
        val eventHint = accessibilityLayoutHint(packageName)
        val relevantRoots = when {
            providerRoots.isNotEmpty() -> providerRoots
            eventHint != null -> allRoots.filter { root -> root.packageName?.toString() == packageName }
            else -> emptyList()
        }
        if (relevantRoots.isEmpty()) {
            if (eventHint != null) {
                screenshotFallback?.request(eventHint)
            } else {
                screenshotFallback?.pause()
            }
            allRoots.forEach { root -> runCatching { root.recycle() } }
            windowInfos.forEach { window -> runCatching { window.recycle() } }
            return
        }
        val effectivePackage = relevantRoots.firstNotNullOfOrNull { root ->
            root.packageName?.toString()?.takeIf { accessibilityLayoutHint(it) != null }
        } ?: packageName
        val readResult = try {
            reader.readDetailed(
                roots = relevantRoots,
                layoutHint = accessibilityLayoutHint(effectivePackage),
                extraText = extraText,
            )
        } finally {
            allRoots.forEach { root -> runCatching { root.recycle() } }
            windowInfos.forEach { window -> runCatching { window.recycle() } }
        }
        val snapshot = readResult.snapshot
        if (snapshot == null) {
            logMissingCard(effectivePackage, readResult.diagnostics, receivedAtNanos)
            screenshotFallback?.request(accessibilityLayoutHint(effectivePackage))
            return
        }
        val output = pipeline.process(snapshot)
        output.unrecognizedLayoutSource?.let { source ->
            Log.w(TAG, "Accessibility saw $source offer marker but parser could not extract fields.")
        }
        val offer = output.offer
        if (offer == null) {
            if (BuildConfig.DEBUG && snapshot.looksLikeOffer()) {
                Log.d(
                    TAG,
                    "LIVE candidate_unparsed provider=${snapshot.layoutHint ?: "unknown"} " +
                        "windows=${providerRoots.size} blocks=${snapshot.blocks.size} " +
                        "latencyMs=${elapsedMs(startedAtNanos)}",
                )
            }
            screenshotFallback?.request(snapshot.layoutHint)
            return
        }
        if (output.isDuplicate) return
        if (!offer.isReadyForLiveAnalysis()) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "LIVE partial provider=${offer.source} hasPayout=${offer.payout.value != null} " +
                        "windows=${providerRoots.size} blocks=${snapshot.blocks.size} " +
                        "legs=${offer.knownLegCount()} latencyMs=${elapsedMs(startedAtNanos)}",
                )
            }
            screenshotFallback?.request(snapshot.layoutHint)
            return
        }
        val result = processor.analyze(offer, AnalysisSource.ACCESSIBILITY) ?: return
        screenshotFallback?.pause()
        // Accessibility can remain enabled while the separate overlay permission is revoked.
        // Keep reading, evaluating and speaking in that state instead of crashing on TYPE_OVERLAY.
        publishInitialOffer(offer, AnalysisSource.ACCESSIBILITY, result)
    }

    override fun onInterrupt() {
        speaker?.stop()
    }

    override fun onDestroy() {
        pendingRead?.let(eventHandler::removeCallbacks)
        pendingRead = null
        screenshotFallback?.close()
        screenshotFallback = null
        runCatching { overlayWindow?.close() }
        overlayWindow = null
        runCatching { speaker?.close() }
        speaker = null
        super.onDestroy()
    }

    private companion object {
        const val TAG = "DriverAccessibility"
        const val ACCESSIBILITY_SETTLE_DELAY_MS = 100L
        const val NO_CARD_LOG_INTERVAL_MS = 1_000L
    }

    private fun br.com.nexo.driver.ocr.OcrTextSnapshot.looksLikeOffer(): Boolean {
        val normalized = blocks.joinToString(" ") { it.text }.lowercase()
        return "r$" in normalized && "km" in normalized &&
            ("min" in normalized || "minuto" in normalized)
    }

    private fun elapsedMs(startedAtNanos: Long): Long =
        (System.nanoTime() - startedAtNanos) / 1_000_000L

    private fun logMissingCard(
        packageName: String?,
        diagnostics: AccessibilityReadDiagnostics,
        receivedAtNanos: Long,
    ) {
        if (!BuildConfig.DEBUG || accessibilityLayoutHint(packageName) == null) return
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastNoCardLogAtMs < NO_CARD_LOG_INTERVAL_MS) return
        lastNoCardLogAtMs = nowMs
        Log.d(
            TAG,
            "LIVE no_card windows=${diagnostics.rootCount} lines=${diagnostics.textLineCount} " +
                "anchors=${diagnostics.cardAnchorCount} payouts=${diagnostics.payoutTokenCount} " +
                "legs=${diagnostics.routeLegCount} eventAgeMs=${elapsedMs(receivedAtNanos)}",
        )
    }

    private fun showOverlay(result: br.com.nexo.driver.analysis.OfferAnalysisResult) {
        if (Settings.canDrawOverlays(this)) {
            runCatching {
                overlayWindow?.show(
                    model = result.overlay,
                    themeMode = result.appearance.themeMode,
                    fontScale = result.appearance.fontScale,
                )
            }.onFailure { failure ->
                Log.e(TAG, "Could not show offer overlay; continuing read-only analysis.", failure)
            }
        } else {
            Log.w(TAG, "Overlay permission is disabled; offer was analyzed without visual card.")
        }
    }

    private fun publishInitialOffer(
        offer: br.com.nexo.driver.offer.NormalizedOffer,
        source: AnalysisSource,
        result: br.com.nexo.driver.analysis.OfferAnalysisResult,
    ) {
        val generation = activeOfferUpdateGate.open(SystemClock.elapsedRealtime())
        showOverlay(result)
        processor.analyzeDestinationUpdateAsync(offer, source) { update ->
            eventHandler.post {
                if (
                    activeOfferUpdateGate.accepts(generation, SystemClock.elapsedRealtime()) &&
                    Settings.canDrawOverlays(this)
                ) {
                    runCatching {
                        overlayWindow?.update(
                            model = update.overlay,
                            themeMode = update.appearance.themeMode,
                            fontScale = update.appearance.fontScale,
                        )
                    }.onFailure { failure ->
                        Log.w(TAG, "Late destination enrichment could not update overlay.", failure)
                    }
                }
            }
        }
    }
}
