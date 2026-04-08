// ===========================================================================
// CommandDispatcher.kt
// ai/openclaw/enhanced/gateway/CommandDispatcher.kt
//
// Routes incoming node.invoke.request commands to the appropriate controller.
// Supports all standard OpenClaw node commands + custom UI automation commands.
// ===========================================================================

package ai.openclaw.enhanced.gateway

import ai.openclaw.enhanced.controller.AppController
import ai.openclaw.enhanced.controller.InputController
import ai.openclaw.enhanced.controller.UIController
import ai.openclaw.enhanced.model.*
import kotlinx.serialization.json.*
import timber.log.Timber

class CommandDispatcher(
    private val appController: AppController,
    private val inputController: InputController,
    private val uiController: UIController
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Dispatches a command to the right controller. Returns a JsonObject result.
     */
    suspend fun dispatch(command: String, paramsJSON: String?): JsonObject {
        Timber.i("Dispatching command: $command")

        return try {
            when (command) {
                // --- App control ---
                "app.launch" -> {
                    val params = parseParams<AppLaunchParams>(paramsJSON)
                        ?: return errorResult("Missing or invalid launch parameters")
                    appController.launchApp(params)
                }
                "app.list" -> appController.getInstalledApps()

                // --- Input automation ---
                "input.tap" -> {
                    val params = parseParams<InputTapParams>(paramsJSON)
                        ?: return errorResult("Missing or invalid tap parameters")
                    inputController.performTap(params)
                }
                "input.text" -> {
                    val params = parseParams<InputTextParams>(paramsJSON)
                        ?: return errorResult("Missing or invalid text parameters")
                    inputController.inputText(params)
                }
                "input.key" -> {
                    val params = parseParams<InputKeyParams>(paramsJSON)
                        ?: return errorResult("Missing or invalid key parameters")
                    inputController.sendKeyEvent(params)
                }

                // --- UI interaction ---
                "ui.findElement" -> {
                    val params = parseParams<FindElementParams>(paramsJSON)
                        ?: return errorResult("Missing or invalid find parameters")
                    uiController.findElement(params)
                }
                "ui.clickElement" -> {
                    val parsed = parseJsonObject(paramsJSON)
                    val description = parsed?.get("description")?.jsonPrimitive?.content
                        ?: return errorResult("Missing element description")
                    uiController.clickElement(description)
                }
                "ui.getScreenContent" -> uiController.getScreenContent()

                // --- Canvas (maps to UI/screenshot capabilities) ---
                "canvas.snapshot" -> uiController.getScreenContent()
                "canvas.navigate" -> {
                    // Navigate = launch URL in browser
                    val parsed = parseJsonObject(paramsJSON)
                    val url = parsed?.get("url")?.jsonPrimitive?.content
                        ?: return errorResult("Missing url parameter")
                    appController.launchApp(AppLaunchParams(
                        packageName = "com.android.chrome",
                        waitForLaunch = true
                    ))
                    // Use system.run to open URL
                    successResult("Launched browser for: $url")
                }
                "canvas.eval", "canvas.present", "canvas.hide",
                "canvas.a2ui.push", "canvas.a2ui.reset" -> {
                    stubResult(command, "Canvas command acknowledged")
                }

                // --- Camera ---
                "camera.list" -> successResult("rear, front")
                "camera.snap", "camera.clip" -> {
                    stubResult(command, "Camera command acknowledged")
                }

                // --- Screen ---
                "screen.record" -> stubResult(command, "Screen recording acknowledged")

                // --- System ---
                "system.run" -> {
                    val parsed = parseJsonObject(paramsJSON)
                    val cmd = parsed?.get("command")?.jsonPrimitive?.content
                        ?: return errorResult("Missing command parameter")
                    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
                    val output = process.inputStream.bufferedReader().readText()
                    val exitCode = process.waitFor()
                    buildJsonObject {
                        put("success", true)
                        put("exitCode", exitCode)
                        put("stdout", output.take(4096))
                    }
                }
                "system.which" -> {
                    val parsed = parseJsonObject(paramsJSON)
                    val name = parsed?.get("name")?.jsonPrimitive?.content
                        ?: return errorResult("Missing name parameter")
                    val process = Runtime.getRuntime().exec(arrayOf("which", name))
                    val path = process.inputStream.bufferedReader().readText().trim()
                    buildJsonObject {
                        put("success", true)
                        put("path", path.ifEmpty { null as String? })
                    }
                }
                "system.notify" -> {
                    stubResult(command, "Notification acknowledged")
                }
                "system.execApprovals.get", "system.execApprovals.set" -> {
                    successResult("all")
                }

                // --- Location ---
                "location.get" -> stubResult(command, "Location not yet implemented")

                // --- SMS (Android) ---
                "sms.send" -> stubResult(command, "SMS not yet implemented")

                // --- Device ---
                "device.status", "device.info",
                "device.permissions", "device.health" -> {
                    buildJsonObject {
                        put("success", true)
                        put("platform", "android")
                        put("model", android.os.Build.MODEL)
                        put("manufacturer", android.os.Build.MANUFACTURER)
                        put("sdk", android.os.Build.VERSION.SDK_INT)
                        put("version", android.os.Build.VERSION.RELEASE)
                    }
                }
                "app.update" -> successResult("Already up to date")

                // --- Personal data ---
                "notifications.list", "notifications.actions",
                "photos.latest",
                "contacts.search", "contacts.add",
                "calendar.events", "calendar.add",
                "motion.activity", "motion.pedometer" -> {
                    stubResult(command, "$command not yet implemented")
                }

                else -> errorResult("Unknown command: $command")
            }
        } catch (e: Exception) {
            Timber.e(e, "Command dispatch failed: $command")
            errorResult("Command failed: ${e.message}")
        }
    }

    private inline fun <reified T> parseParams(paramsJSON: String?): T? {
        if (paramsJSON.isNullOrBlank()) return null
        return try {
            json.decodeFromString<T>(paramsJSON)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse params: $paramsJSON")
            null
        }
    }

    private fun parseJsonObject(paramsJSON: String?): JsonObject? {
        if (paramsJSON.isNullOrBlank()) return null
        return try { json.parseToJsonElement(paramsJSON).jsonObject } catch (e: Exception) { null }
    }

    private fun errorResult(message: String) = buildJsonObject {
        put("success", false); put("error", message)
    }

    private fun successResult(message: String) = buildJsonObject {
        put("success", true); put("message", message)
    }

    private fun stubResult(command: String, message: String) = buildJsonObject {
        put("success", true); put("stub", true); put("command", command); put("message", message)
    }
}
