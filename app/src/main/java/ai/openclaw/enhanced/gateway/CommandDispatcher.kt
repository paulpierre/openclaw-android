// ===========================================================================
// CommandDispatcher.kt
// ai/openclaw/enhanced/gateway/CommandDispatcher.kt
//
// Routes incoming node.invoke.request commands to the appropriate controller.
// Parses paramsJSON from the invoke request and delegates to AppController,
// InputController, or UIController. Returns JSON results.
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
     * Dispatches a command string with its JSON params to the right controller.
     * Returns a JsonObject result (always contains "success" key).
     */
    suspend fun dispatch(command: String, paramsJSON: String?): JsonObject {
        Timber.i("Dispatching command: $command")

        return try {
            when (command) {
                "app.launch" -> {
                    val params = parseParams<AppLaunchParams>(paramsJSON)
                        ?: return errorResult("Missing or invalid launch parameters")
                    appController.launchApp(params)
                }

                "app.list" -> {
                    appController.getInstalledApps()
                }

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

                "ui.getScreenContent" -> {
                    uiController.getScreenContent()
                }

                else -> {
                    errorResult("Unknown command: $command")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Command dispatch failed: $command")
            errorResult("Command failed: ${e.message}")
        }
    }

    /**
     * Deserializes a JSON string into the given type. Returns null on failure.
     */
    private inline fun <reified T> parseParams(paramsJSON: String?): T? {
        if (paramsJSON.isNullOrBlank()) return null
        return try {
            json.decodeFromString<T>(paramsJSON)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse params: $paramsJSON")
            null
        }
    }

    /**
     * Parses a JSON string into a JsonObject. Returns null on failure.
     */
    private fun parseJsonObject(paramsJSON: String?): JsonObject? {
        if (paramsJSON.isNullOrBlank()) return null
        return try {
            json.parseToJsonElement(paramsJSON).jsonObject
        } catch (e: Exception) {
            null
        }
    }

    private fun errorResult(message: String): JsonObject {
        return JsonObject(mapOf(
            "success" to JsonPrimitive(false),
            "error" to JsonPrimitive(message)
        ))
    }
}
