// ===========================================================================
// EnhancedAccessibilityService.kt
// ai/openclaw/enhanced/service/EnhancedAccessibilityService.kt
//
// Accessibility service that provides UI automation and screenshot capture.
// Uses AccessibilityService.takeScreenshot() (API 30+) for screen capture,
// which runs with system-level privileges — unlike screencap which fails
// when executed from the app's UID via Runtime.exec().
// ===========================================================================

package ai.openclaw.enhanced.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.resume

class EnhancedAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: EnhancedAccessibilityService? = null
        fun getInstance(): EnhancedAccessibilityService? = instance
    }

    // Single-thread executor for screenshot callbacks
    private val screenshotExecutor = Executors.newSingleThreadExecutor()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Timber.i("EnhancedAccessibilityService connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        screenshotExecutor.shutdown()
        Timber.i("EnhancedAccessibilityService destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We use the service for gestures, UI interaction, and screenshots.
        // No event processing needed currently.
    }

    override fun onInterrupt() {
        Timber.w("EnhancedAccessibilityService interrupted")
    }

    // -----------------------------------------------------------------------
    // Screenshot via AccessibilityService.takeScreenshot() — API 30+
    // -----------------------------------------------------------------------

    /**
     * Takes a screenshot using the AccessibilityService API.
     * This works without shell/root because the accessibility service has
     * system-level screen capture privileges when canTakeScreenshot=true.
     *
     * Calls back with the Bitmap, or null on failure.
     */
    fun takeScreenshot(callback: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Timber.w("takeScreenshot requires API 30+, current: ${Build.VERSION.SDK_INT}")
            callback(null)
            return
        }

        try {
            // AccessibilityService.takeScreenshot(displayId, executor, callback)
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                screenshotExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        val bitmap = Bitmap.wrapHardwareBuffer(
                            screenshotResult.hardwareBuffer,
                            screenshotResult.colorSpace
                        )
                        screenshotResult.hardwareBuffer.close()

                        if (bitmap != null) {
                            // Hardware bitmaps can't be compressed directly;
                            // copy to a software bitmap first.
                            val softBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                            bitmap.recycle()
                            callback(softBitmap)
                        } else {
                            Timber.e("takeScreenshot: wrapHardwareBuffer returned null")
                            callback(null)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Timber.e("takeScreenshot failed with error code: $errorCode")
                        callback(null)
                    }
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "takeScreenshot threw an exception")
            callback(null)
        }
    }

    /**
     * Suspend function that captures a screenshot and returns it as a
     * base64-encoded PNG string. Returns null if the screenshot fails.
     */
    suspend fun takeScreenshotBase64(): String? = suspendCancellableCoroutine { cont ->
        takeScreenshot { bitmap ->
            if (bitmap == null) {
                cont.resume(null)
                return@takeScreenshot
            }

            try {
                // Compress to PNG, then base64-encode
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                bitmap.recycle()

                val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                cont.resume(base64)
            } catch (e: Exception) {
                Timber.e(e, "Failed to encode screenshot to base64")
                bitmap.recycle()
                cont.resume(null)
            }
        }
    }
}
