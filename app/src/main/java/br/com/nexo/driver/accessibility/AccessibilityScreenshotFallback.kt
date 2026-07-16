package br.com.nexo.driver.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi
import br.com.nexo.driver.capture.visual.OfferCardVisualDetector
import br.com.nexo.driver.offer.NormalizedOffer
import br.com.nexo.driver.offer.OfferSource
import br.com.nexo.driver.ocr.OfferOcrPipeline
import br.com.nexo.driver.ocr.OcrTextSnapshot
import br.com.nexo.driver.ocr.mlkit.MlKitBitmapOcrEngine
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Automatic local screenshot/OCR complement when Accessibility text is absent or incomplete. */
internal class AccessibilityScreenshotFallback(
    private val service: AccessibilityService,
    private val onOffer: (NormalizedOffer, ScreenshotReadMetrics) -> Unit,
) : Closeable {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "AccessibilityScreenshotOcr").apply { isDaemon = true }
    }
    private val inFlight = AtomicBoolean(false)
    private val detector = OfferCardVisualDetector()
    private val ocrEngine = MlKitBitmapOcrEngine()
    private val pipeline = OfferOcrPipeline()
    private var providerHint: String? = null
    private var active = false
    // Zero permits the first wide retry. Long.MIN_VALUE overflows when subtracted
    // from elapsedRealtime(), which used to suppress every wide retry forever.
    private var lastWideRetryMs = 0L
    private var scheduled = false
    private var lastDiagnosticText: String? = null
    @Volatile private var closed = false

    fun request(provider: String?) {
        if (closed || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        providerHint = provider
        active = true
        schedule(delayMs = 0L, wide = prefersWideCapture())
    }

    fun pause() {
        active = false
        scheduled = false
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun schedule(delayMs: Long, wide: Boolean) {
        if (closed || scheduled) return
        scheduled = true
        mainHandler.postDelayed(
            {
                scheduled = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !closed && active) {
                    capture(wide)
                }
            },
            delayMs,
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun capture(wide: Boolean) {
        if (!inFlight.compareAndSet(false, true)) return
        val requestedAtNanos = System.nanoTime()
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            worker,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    processScreenshot(result, requestedAtNanos, wide)
                }

                override fun onFailure(errorCode: Int) {
                    inFlight.set(false)
                    Log.w(TAG, "screenshot_failed code=$errorCode")
                    mainHandler.post { schedule(CAPTURE_INTERVAL_MS, wide = prefersWideCapture()) }
                }
            },
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun processScreenshot(
        result: AccessibilityService.ScreenshotResult,
        requestedAtNanos: Long,
        wide: Boolean,
    ) {
        val buffer = result.hardwareBuffer
        var screenBitmap: Bitmap? = null
        var cropBitmap: Bitmap? = null
        try {
            val hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                ?: error("Screenshot HardwareBuffer could not be wrapped.")
            screenBitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
            hardwareBitmap.recycle()
            buffer.close()

            val region = if (wide) {
                detector.fallback(screenBitmap.width, screenBitmap.height).copy(
                    left = 0,
                    top = (screenBitmap.height * WIDE_RETRY_TOP_FRACTION).toInt(),
                    right = screenBitmap.width,
                )
            } else {
                detector.detect(screenBitmap, providerHint)
            }
            cropBitmap = Bitmap.createBitmap(
                screenBitmap,
                region.left,
                region.top,
                region.width,
                region.height,
            )
            val ocrStartedAtNanos = System.nanoTime()
            val blocks = ocrEngine.recognize(cropBitmap)
            val ocrMs = elapsedMs(ocrStartedAtNanos)
            val output = pipeline.process(
                OcrTextSnapshot(
                    blocks = blocks,
                    capturedAtEpochMs = System.currentTimeMillis(),
                    layoutHint = providerHint,
                ),
            )
            val offer = output.offer
            val metrics = ScreenshotReadMetrics(
                totalMs = elapsedMs(requestedAtNanos),
                ocrMs = ocrMs,
                cropTop = region.top,
                cropBottom = region.bottom,
                visualConfidence = region.confidence,
                cropStrategy = if (wide) "WIDE_RETRY" else region.strategy.name,
            )
            if (offer != null && output.isDuplicate) {
                // Repeated accessibility events for the same visible card must not keep OCR hot.
                active = false
            } else if (
                offer != null &&
                offer.isReadyForLiveAnalysis() &&
                offer.isPlausible()
            ) {
                // A valid offer ends the burst. A new accessibility event explicitly rearms it.
                active = false
                mainHandler.post { if (!closed) onOffer(offer, metrics) }
            } else {
                logChangedDiagnostic(output.raw.text, offer)
                Log.d(
                    TAG,
                    "ocr_incomplete provider=${providerHint ?: "unknown"} blocks=${blocks.size} " +
                        "strategy=${metrics.cropStrategy} confidence=${metrics.visualConfidence} " +
                        "crop=${metrics.cropTop}-${metrics.cropBottom} ocrMs=${metrics.ocrMs} totalMs=${metrics.totalMs}",
                )
                val nowMs = SystemClock.elapsedRealtime()
                if (!wide && nowMs - lastWideRetryMs >= WIDE_RETRY_COOLDOWN_MS) {
                    lastWideRetryMs = nowMs
                    mainHandler.post { schedule(WIDE_RETRY_DELAY_MS, wide = true) }
                }
            }
        } catch (failure: Exception) {
            runCatching { buffer.close() }
            Log.w(TAG, "screenshot_ocr_failed reason=${failure.javaClass.simpleName}")
        } finally {
            cropBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
            screenBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
            inFlight.set(false)
            if (active) {
                mainHandler.post { schedule(CAPTURE_INTERVAL_MS, wide = prefersWideCapture()) }
            }
        }
    }

    override fun close() {
        closed = true
        mainHandler.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        runCatching { ocrEngine.close() }
    }

    private fun NormalizedOffer.isPlausible(): Boolean {
        val payoutCents = payout.value?.cents ?: return false
        val durations = listOfNotNull(pickup.duration.value?.seconds, trip.duration.value?.seconds)
        val distances = listOfNotNull(pickup.distance.value?.meters, trip.distance.value?.meters)
        val rating = passenger.rating.value
        return payoutCents in 200L..50_000L &&
            durations.all { it in 60L..36_000L } &&
            distances.all { it in 100L..350_000L } &&
            (rating == null || rating in 400L..500L)
    }

    private fun prefersWideCapture(): Boolean = providerHint.equals("uber", ignoreCase = true)

    private fun logChangedDiagnostic(rawText: String, offer: NormalizedOffer?) {
        val diagnostic = "chars=${rawText.length} lines=${rawText.lineSequence().count()} " +
            "parsed=${offer != null} knownLegs=${offer?.knownLegCount() ?: 0}"
        if (diagnostic == lastDiagnosticText) return
        lastDiagnosticText = diagnostic
        Log.d(TAG, "ocr_diagnostic $diagnostic")
    }

    private fun elapsedMs(startedAtNanos: Long): Long =
        (System.nanoTime() - startedAtNanos) / 1_000_000L

    private companion object {
        const val TAG = "AccessibilityOcr"
        const val CAPTURE_INTERVAL_MS = 150L
        const val WIDE_RETRY_DELAY_MS = 120L
        const val WIDE_RETRY_COOLDOWN_MS = 2_500L
        const val WIDE_RETRY_TOP_FRACTION = 0.38f
    }
}

internal data class ScreenshotReadMetrics(
    val totalMs: Long,
    val ocrMs: Long,
    val cropTop: Int,
    val cropBottom: Int,
    val visualConfidence: Float,
    val cropStrategy: String,
)
