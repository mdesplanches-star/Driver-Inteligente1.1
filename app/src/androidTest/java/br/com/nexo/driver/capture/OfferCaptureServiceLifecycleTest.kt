package br.com.nexo.driver.capture

import android.app.Activity
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises [OfferCaptureService] against the real [android.app.Service] lifecycle instead of
 * pure unit logic, which the existing `app/src/test` suite cannot reach (Service/Context are not
 * available on the JVM unit-test classpath).
 */
@RunWith(AndroidJUnit4::class)
class OfferCaptureServiceLifecycleTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        OfferCaptureService.stop(context)
    }

    @Test
    fun deniedProjectionConsentStopsTheServiceWithoutStartingCapture() {
        // RESULT_CANCELED mirrors the user dismissing the system screen-capture consent dialog.
        // onStartCommand must tear the session down immediately rather than start a
        // MediaProjection/VirtualDisplay/OCR pipeline with no valid grant.
        OfferCaptureService.start(context, Activity.RESULT_CANCELED, Intent())

        assertFalse(waitUntilInactiveOrTimeout())
    }

    /** Polls the real, disk-backed runtime state instead of assuming synchronous IPC delivery. */
    private fun waitUntilInactiveOrTimeout(timeoutMillis: Long = 5_000L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (!OfferCaptureService.isActive(context)) return false
            Thread.sleep(100)
        }
        return OfferCaptureService.isActive(context)
    }
}
