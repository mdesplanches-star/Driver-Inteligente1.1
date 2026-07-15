package br.com.nexo.driver.ocr.mlkit

import android.graphics.Bitmap
import br.com.nexo.driver.ocr.LocalOcrEngine
import br.com.nexo.driver.ocr.OcrTextBlock
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * On-device ML Kit implementation of [LocalOcrEngine] for bitmap screen captures.
 *
 * Contract:
 * - The caller owns [Bitmap] and must keep it valid and immutable for the duration of
 *   [recognize]. It is never recycled or retained after this method returns.
 * - [recognize] is synchronous to satisfy [LocalOcrEngine], but ML Kit is asynchronous under
 *   the hood. It must therefore be invoked from a worker thread, never from the main thread.
 * - Returned blocks are normalised (trimmed, empty blocks discarded) and sorted top-to-bottom,
 *   then left-to-right, yielding deterministic [OcrTextBlock.readingOrder] values.
 * - ML Kit's Android text API does not expose a per-block confidence score, so every block is
 *   emitted with the neutral confidence value `1f`. Downstream validation must rely on parsing
 *   and field-level plausibility checks rather than this placeholder.
 *
 * Thread safety: calls to [recognize] and [close] are serialised. A caller may share one engine
 * across capture workers safely; concurrent calls are processed one at a time. After [close],
 * [recognize] throws [IllegalStateException].
 */
class MlKitBitmapOcrEngine(
    private val recognizer: com.google.mlkit.vision.text.TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
    private val timeoutMillis: Long = DEFAULT_RECOGNITION_TIMEOUT_MILLIS,
) : LocalOcrEngine<Bitmap>, Closeable {

    init {
        require(timeoutMillis > 0L) { "OCR recognition timeout must be greater than zero." }
    }

    private var isClosed = false

    /**
     * Runs ML Kit's Latin-script recognizer and waits up to [timeoutMillis] for one result.
     *
     * @throws MlKitOcrException when ML Kit fails or does not finish before the timeout.
     * @throws IllegalStateException when called after [close].
     */
    @Synchronized
    override fun recognize(frame: Bitmap): List<OcrTextBlock> {
        check(!isClosed) { "MlKitBitmapOcrEngine is closed." }

        val image = InputImage.fromBitmap(frame, 0)
        val text = try {
            Tasks.await(recognizer.process(image), timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw MlKitOcrException("ML Kit text recognition was interrupted.", exception)
        } catch (exception: TimeoutException) {
            throw MlKitOcrException(
                "ML Kit text recognition exceeded the ${timeoutMillis}ms timeout.",
                exception,
            )
        } catch (exception: ExecutionException) {
            throw MlKitOcrException("ML Kit text recognition failed.", exception.cause ?: exception)
        } catch (exception: Exception) {
            throw MlKitOcrException("ML Kit text recognition failed.", exception)
        }

        return text.textBlocks
            .mapIndexedNotNull { originalIndex, block -> block.toCandidate(originalIndex) }
            .sortedWith(
                compareBy<TextBlockCandidate> { it.top }
                    .thenBy { it.left }
                    .thenBy { it.originalIndex },
            )
            .mapIndexed { readingOrder, candidate ->
                OcrTextBlock(
                    text = candidate.text,
                    readingOrder = readingOrder,
                )
            }
    }

    @Synchronized
    override fun close() {
        if (!isClosed) {
            isClosed = true
            recognizer.close()
        }
    }

    private fun Text.TextBlock.toCandidate(originalIndex: Int): TextBlockCandidate? {
        val normalizedText = text.trim()
        if (normalizedText.isEmpty()) return null

        val bounds = boundingBox
        return TextBlockCandidate(
            text = normalizedText,
            top = bounds?.top ?: Int.MAX_VALUE,
            left = bounds?.left ?: Int.MAX_VALUE,
            originalIndex = originalIndex,
        )
    }

    private data class TextBlockCandidate(
        val text: String,
        val top: Int,
        val left: Int,
        val originalIndex: Int,
    )
}

/** Failure from the bounded, local ML Kit recognition operation. */
class MlKitOcrException(message: String, cause: Throwable) : RuntimeException(message, cause)

/** Keeps a single stalled recognition from consuming the overlay's one-second response budget. */
const val DEFAULT_RECOGNITION_TIMEOUT_MILLIS = 750L
