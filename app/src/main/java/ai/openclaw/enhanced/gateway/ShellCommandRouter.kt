// ===========================================================================
// ShellCommandRouter.kt
// ai/openclaw/enhanced/gateway/ShellCommandRouter.kt
//
// Routes shell commands from system.run. Most commands run via Runtime.exec()
// normally (input tap, am start, etc.). However, "screencap" and
// "uiautomator dump" require elevated privileges the app UID lacks, so they
// are intercepted and handled via AccessibilityService APIs instead.
// ===========================================================================

package ai.openclaw.enhanced.gateway

import ai.openclaw.enhanced.controller.UIController
import ai.openclaw.enhanced.service.EnhancedAccessibilityService
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber

class ShellCommandRouter(private val uiController: UIController) {

    /**
     * Executes a shell command, intercepting privileged commands that would
     * fail under the app's UID.
     */
    suspend fun execute(cmd: String): JsonObject {
        val trimmed = cmd.trim()

        // Intercept: screencap -> use AccessibilityService screenshot
        if (trimmed.startsWith("screencap")) {
            return handleScreencap(trimmed)
        }

        // Intercept: uiautomator dump -> use accessibility tree
        if (trimmed.startsWith("uiautomator dump") ||
            trimmed.startsWith("uiautomator dum")) {
            return handleUiautomatorDump()
        }

        // Default: run via Runtime.exec() (works for input, am, pm, etc.)
        return execShellCommand(cmd)
    }

    // -----------------------------------------------------------------------
    // screencap interception
    // -----------------------------------------------------------------------

    /**
     * Handles "screencap" by taking a screenshot via AccessibilityService.
     * If the command specifies an output path (e.g. "screencap -p /path"),
     * we write the PNG bytes there. Otherwise we return base64 in stdout.
     */
    private suspend fun handleScreencap(cmd: String): JsonObject {
        val service = EnhancedAccessibilityService.getInstance()
            ?: return errorResult("Accessibility service not available for screencap")

        val base64 = service.takeScreenshotBase64()
            ?: return errorResult("Screenshot capture failed")

        // Check if the command specifies an output file path.
        // Typical usage: "screencap -p /sdcard/screen.png"
        val outputPath = extractOutputPath(cmd)

        if (outputPath != null) {
            return writeScreenshotToFile(base64, outputPath)
        }

        // No output path — return base64 data in stdout
        return buildJsonObject {
            put("success", true)
            put("exitCode", 0)
            put("stdout", base64.take(4096))
            put("screenshotBase64", base64)
        }
    }

    /**
     * Writes base64-decoded PNG bytes to the given file path.
     */
    private fun writeScreenshotToFile(base64: String, path: String): JsonObject {
        return try {
            val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
            java.io.File(path).writeBytes(bytes)
            buildJsonObject {
                put("success", true)
                put("exitCode", 0)
                put("stdout", "Screenshot saved to $path (via AccessibilityService)")
            }
        } catch (e: Exception) {
            errorResult("Failed to write screenshot to $path: ${e.message}")
        }
    }

    /**
     * Parses the output file path from a screencap command.
     * Handles: "screencap /path", "screencap -p /path", "screencap -j /path"
     * Returns null if no path argument is found.
     */
    private fun extractOutputPath(cmd: String): String? {
        val parts = cmd.trim().split("\\s+".toRegex())
        return parts.lastOrNull { it.startsWith("/") }
    }

    // -----------------------------------------------------------------------
    // uiautomator dump interception
    // -----------------------------------------------------------------------

    /**
     * Handles "uiautomator dump" by returning the accessibility tree.
     * The real uiautomator dump requires shell UID — we use the
     * accessibility node tree as a substitute.
     */
    private fun handleUiautomatorDump(): JsonObject {
        val screenContent = uiController.getScreenContent()
        val xmlOutput = "<hierarchy><!-- Provided via AccessibilityService --></hierarchy>"
        return buildJsonObject {
            put("success", true)
            put("exitCode", 0)
            put("stdout", xmlOutput)
            put("accessibilityTree", screenContent)
        }
    }

    // -----------------------------------------------------------------------
    // Default shell execution
    // -----------------------------------------------------------------------

    /**
     * Runs a command via Runtime.exec(). Works for non-privileged commands
     * like "input tap", "am start", "pm list packages", etc.
     */
    private fun execShellCommand(cmd: String): JsonObject {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        return buildJsonObject {
            put("success", true)
            put("exitCode", exitCode)
            put("stdout", output.take(4096))
        }
    }

    private fun errorResult(message: String) = buildJsonObject {
        put("success", false); put("error", message)
    }
}
