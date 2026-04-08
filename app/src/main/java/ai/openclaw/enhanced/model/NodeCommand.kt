package ai.openclaw.enhanced.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class NodeCommand(
    val id: String,
    val command: String,
    val params: JsonElement? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class NodeResponse(
    val id: String,
    val success: Boolean,
    val result: JsonElement? = null,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class AppLaunchParams(
    val packageName: String,
    val activity: String? = null,
    val waitForLaunch: Boolean = true
)

@Serializable
data class InputTapParams(
    val x: Int,
    val y: Int,
    val duration: Long = 100L
)

@Serializable
data class InputTextParams(
    val text: String,
    val clearFirst: Boolean = false
)

@Serializable
data class InputKeyParams(
    val keycode: String,
    val metaKeys: List<String> = emptyList()
)

@Serializable
data class FindElementParams(
    val description: String,
    val timeout: Long = 5000L
)

@Serializable
data class ElementResult(
    val found: Boolean,
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val text: String? = null,
    val contentDescription: String? = null
)