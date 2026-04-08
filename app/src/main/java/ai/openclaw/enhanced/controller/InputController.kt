package ai.openclaw.enhanced.controller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.view.KeyEvent
import ai.openclaw.enhanced.model.InputKeyParams
import ai.openclaw.enhanced.model.InputTapParams
import ai.openclaw.enhanced.model.InputTextParams
import ai.openclaw.enhanced.service.EnhancedAccessibilityService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import kotlin.coroutines.resume

class InputController(private val context: Context) {
    
    suspend fun performTap(params: InputTapParams): JsonObject {
        return try {
            val accessibilityService = EnhancedAccessibilityService.getInstance()
                ?: return JsonObject(mapOf(
                    "success" to JsonPrimitive(false),
                    "error" to JsonPrimitive("Accessibility service not enabled")
                ))
            
            Timber.i("Performing tap at (${params.x}, ${params.y})")
            
            val success = performGestureClick(
                accessibilityService,
                params.x.toFloat(),
                params.y.toFloat(),
                params.duration
            )
            
            if (success) {
                Timber.i("Successfully performed tap at (${params.x}, ${params.y})")
                JsonObject(mapOf(
                    "success" to JsonPrimitive(true),
                    "x" to JsonPrimitive(params.x),
                    "y" to JsonPrimitive(params.y)
                ))
            } else {
                JsonObject(mapOf(
                    "success" to JsonPrimitive(false),
                    "error" to JsonPrimitive("Failed to perform tap gesture")
                ))
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to perform tap")
            JsonObject(mapOf(
                "success" to JsonPrimitive(false),
                "error" to JsonPrimitive("Tap failed: ${e.message}")
            ))
        }
    }
    
    suspend fun inputText(params: InputTextParams): JsonObject {
        return try {
            val accessibilityService = EnhancedAccessibilityService.getInstance()
                ?: return JsonObject(mapOf(
                    "success" to JsonPrimitive(false),
                    "error" to JsonPrimitive("Accessibility service not enabled")
                ))
            
            Timber.i("Inputting text: ${params.text}")
            
            // If clearFirst is true, select all text in the focused node via ACTION_SET_SELECTION
            if (params.clearFirst) {
                val selectNode = accessibilityService.rootInActiveWindow
                    ?.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
                if (selectNode != null) {
                    val args = android.os.Bundle().apply {
                        putInt(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                        putInt(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, Int.MAX_VALUE)
                    }
                    selectNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
                }
                Thread.sleep(100)
            }

            // Use clipboard to input text (more reliable than key events)
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clipData = android.content.ClipData.newPlainText("openclaw_input", params.text)
            clipboardManager.setPrimaryClip(clipData)

            // Paste the text via the focused accessibility node using ACTION_PASTE
            val focusedNode = accessibilityService.rootInActiveWindow
                ?.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
            val pasteSuccess = focusedNode?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_PASTE) ?: false
            
            if (pasteSuccess) {
                Timber.i("Successfully input text: ${params.text}")
                JsonObject(mapOf(
                    "success" to JsonPrimitive(true),
                    "text" to JsonPrimitive(params.text),
                    "method" to JsonPrimitive("clipboard")
                ))
            } else {
                JsonObject(mapOf(
                    "success" to JsonPrimitive(false),
                    "error" to JsonPrimitive("Failed to paste text")
                ))
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to input text")
            JsonObject(mapOf(
                "success" to JsonPrimitive(false),
                "error" to JsonPrimitive("Text input failed: ${e.message}")
            ))
        }
    }
    
    fun sendKeyEvent(params: InputKeyParams): JsonObject {
        return try {
            val accessibilityService = EnhancedAccessibilityService.getInstance()
                ?: return JsonObject(mapOf(
                    "success" to JsonPrimitive(false),
                    "error" to JsonPrimitive("Accessibility service not enabled")
                ))
            
            val keyCode = when (params.keycode.uppercase()) {
                "BACK" -> KeyEvent.KEYCODE_BACK
                "HOME" -> KeyEvent.KEYCODE_HOME
                "MENU" -> KeyEvent.KEYCODE_MENU
                "RECENT_APPS", "RECENTS" -> KeyEvent.KEYCODE_APP_SWITCH
                "ENTER" -> KeyEvent.KEYCODE_ENTER
                "SPACE" -> KeyEvent.KEYCODE_SPACE
                "DEL", "DELETE" -> KeyEvent.KEYCODE_DEL
                "TAB" -> KeyEvent.KEYCODE_TAB
                "UP" -> KeyEvent.KEYCODE_DPAD_UP
                "DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
                "LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
                "RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
                "CENTER" -> KeyEvent.KEYCODE_DPAD_CENTER
                else -> {
                    return JsonObject(mapOf(
                        "success" to JsonPrimitive(false),
                        "error" to JsonPrimitive("Unsupported key code: ${params.keycode}")
                    ))
                }
            }
            
            Timber.i("Sending key event: ${params.keycode} (code: $keyCode)")
            
            val success = when (params.keycode.uppercase()) {
                "BACK" -> accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                "HOME" -> accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                "RECENT_APPS", "RECENTS" -> accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                else -> {
                    // For other keys, we'd need to send actual key events
                    // This requires INJECT_EVENTS permission which needs system signature
                    false
                }
            }
            
            if (success) {
                Timber.i("Successfully sent key event: ${params.keycode}")
                JsonObject(mapOf(
                    "success" to JsonPrimitive(true),
                    "keycode" to JsonPrimitive(params.keycode)
                ))
            } else {
                JsonObject(mapOf(
                    "success" to JsonPrimitive(false),
                    "error" to JsonPrimitive("Failed to send key event")
                ))
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to send key event")
            JsonObject(mapOf(
                "success" to JsonPrimitive(false),
                "error" to JsonPrimitive("Key event failed: ${e.message}")
            ))
        }
    }
    
    private suspend fun performGestureClick(
        service: AccessibilityService,
        x: Float,
        y: Float,
        duration: Long
    ): Boolean = suspendCancellableCoroutine { continuation ->
        
        val path = Path().apply {
            moveTo(x, y)
        }
        
        val gestureDescription = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                continuation.resume(true)
            }
            
            override fun onCancelled(gestureDescription: GestureDescription?) {
                continuation.resume(false)
            }
        }
        
        val result = service.dispatchGesture(gestureDescription, callback, null)
        
        if (!result) {
            continuation.resume(false)
        }
    }
}