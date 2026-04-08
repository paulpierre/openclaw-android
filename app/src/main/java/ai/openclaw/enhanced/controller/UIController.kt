package ai.openclaw.enhanced.controller

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import ai.openclaw.enhanced.model.ElementResult
import ai.openclaw.enhanced.model.FindElementParams
import ai.openclaw.enhanced.service.EnhancedAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber

class UIController(private val context: Context) {
    
    suspend fun findElement(params: FindElementParams): JsonObject {
        return try {
            val accessibilityService = EnhancedAccessibilityService.getInstance()
                ?: return JsonObject(mapOf(
                    "success" to JsonPrimitive(false),
                    "error" to JsonPrimitive("Accessibility service not enabled")
                ))
            
            Timber.i("Finding element with description: ${params.description}")
            
            val element = withTimeoutOrNull(params.timeout) {
                searchForElement(accessibilityService, params.description)
            }
            
            if (element != null) {
                val bounds = android.graphics.Rect()
                element.getBoundsInScreen(bounds)
                
                val result = ElementResult(
                    found = true,
                    x = bounds.centerX(),
                    y = bounds.centerY(),
                    width = bounds.width(),
                    height = bounds.height(),
                    text = element.text?.toString(),
                    contentDescription = element.contentDescription?.toString()
                )
                
                Timber.i("Found element: ${result}")
                
                JsonObject(mapOf(
                    "success" to JsonPrimitive(true),
                    "found" to JsonPrimitive(result.found),
                    "x" to JsonPrimitive(result.x),
                    "y" to JsonPrimitive(result.y),
                    "width" to JsonPrimitive(result.width),
                    "height" to JsonPrimitive(result.height),
                    "text" to JsonPrimitive(result.text ?: ""),
                    "contentDescription" to JsonPrimitive(result.contentDescription ?: "")
                ))
            } else {
                Timber.w("Element not found: ${params.description}")
                JsonObject(mapOf(
                    "success" to JsonPrimitive(true),
                    "found" to JsonPrimitive(false),
                    "error" to JsonPrimitive("Element not found within timeout")
                ))
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to find element")
            JsonObject(mapOf(
                "success" to JsonPrimitive(false),
                "error" to JsonPrimitive("Element search failed: ${e.message}")
            ))
        }
    }
    
    suspend fun clickElement(description: String): JsonObject {
        return try {
            val accessibilityService = EnhancedAccessibilityService.getInstance()
                ?: return JsonObject(mapOf(
                    "success" to JsonPrimitive(false),
                    "error" to JsonPrimitive("Accessibility service not enabled")
                ))
            
            Timber.i("Clicking element with description: $description")
            
            val element = withTimeoutOrNull(5000) {
                searchForElement(accessibilityService, description)
            }
            
            if (element != null && element.isClickable) {
                val success = element.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                
                if (success) {
                    Timber.i("Successfully clicked element: $description")
                    JsonObject(mapOf(
                        "success" to JsonPrimitive(true),
                        "description" to JsonPrimitive(description)
                    ))
                } else {
                    JsonObject(mapOf(
                        "success" to JsonPrimitive(false),
                        "error" to JsonPrimitive("Failed to perform click action")
                    ))
                }
            } else {
                JsonObject(mapOf(
                    "success" to JsonPrimitive(false),
                    "error" to JsonPrimitive("Element not found or not clickable")
                ))
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to click element")
            JsonObject(mapOf(
                "success" to JsonPrimitive(false),
                "error" to JsonPrimitive("Click failed: ${e.message}")
            ))
        }
    }
    
    fun getScreenContent(): JsonObject {
        return try {
            val accessibilityService = EnhancedAccessibilityService.getInstance()
                ?: return JsonObject(mapOf(
                    "success" to JsonPrimitive(false),
                    "error" to JsonPrimitive("Accessibility service not enabled")
                ))
            
            val rootNode = accessibilityService.rootInActiveWindow
                ?: return JsonObject(mapOf(
                    "success" to JsonPrimitive(false),
                    "error" to JsonPrimitive("No active window")
                ))
            
            val elements = mutableListOf<JsonObject>()
            extractElements(rootNode, elements)
            
            JsonObject(mapOf(
                "success" to JsonPrimitive(true),
                "elementCount" to JsonPrimitive(elements.size),
                "elements" to JsonObject(elements.mapIndexed { index, element -> 
                    index.toString() to element 
                }.toMap())
            ))
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to get screen content")
            JsonObject(mapOf(
                "success" to JsonPrimitive(false),
                "error" to JsonPrimitive("Screen content failed: ${e.message}")
            ))
        }
    }
    
    private suspend fun searchForElement(
        service: AccessibilityService,
        description: String
    ): AccessibilityNodeInfo? {
        var retryCount = 0
        val maxRetries = 5
        
        while (retryCount < maxRetries) {
            val rootNode = service.rootInActiveWindow
            if (rootNode != null) {
                val element = findNodeByDescription(rootNode, description)
                if (element != null) {
                    return element
                }
            }
            
            retryCount++
            delay(500) // Wait before retry
        }
        
        return null
    }
    
    private fun findNodeByDescription(
        node: AccessibilityNodeInfo,
        description: String
    ): AccessibilityNodeInfo? {
        val lowerDescription = description.lowercase()
        
        // Check current node
        val nodeText = node.text?.toString()?.lowercase()
        val nodeContentDesc = node.contentDescription?.toString()?.lowercase()
        val nodeClassName = node.className?.toString()?.lowercase()
        
        if ((nodeText != null && nodeText.contains(lowerDescription)) ||
            (nodeContentDesc != null && nodeContentDesc.contains(lowerDescription)) ||
            (nodeClassName != null && nodeClassName.contains(lowerDescription))) {
            return node
        }
        
        // Search children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findNodeByDescription(child, description)
                if (result != null) {
                    return result
                }
            }
        }
        
        return null
    }
    
    private fun extractElements(node: AccessibilityNodeInfo, elements: MutableList<JsonObject>) {
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        
        if (bounds.width() > 0 && bounds.height() > 0) {
            val element = JsonObject(mapOf(
                "className" to JsonPrimitive(node.className?.toString() ?: ""),
                "text" to JsonPrimitive(node.text?.toString() ?: ""),
                "contentDescription" to JsonPrimitive(node.contentDescription?.toString() ?: ""),
                "bounds" to JsonObject(mapOf(
                    "left" to JsonPrimitive(bounds.left),
                    "top" to JsonPrimitive(bounds.top),
                    "right" to JsonPrimitive(bounds.right),
                    "bottom" to JsonPrimitive(bounds.bottom)
                )),
                "clickable" to JsonPrimitive(node.isClickable),
                "focusable" to JsonPrimitive(node.isFocusable),
                "enabled" to JsonPrimitive(node.isEnabled)
            ))
            
            elements.add(element)
        }
        
        // Process children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractElements(child, elements)
            }
        }
    }
}