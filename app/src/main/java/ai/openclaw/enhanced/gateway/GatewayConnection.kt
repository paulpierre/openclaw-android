package ai.openclaw.enhanced.gateway

import android.content.Context
import ai.openclaw.enhanced.controller.AppController
import ai.openclaw.enhanced.controller.InputController
import ai.openclaw.enhanced.controller.UIController
import ai.openclaw.enhanced.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import timber.log.Timber
import java.net.URI
import java.util.UUID

class GatewayConnection(
    private val context: Context,
    private val appController: AppController,
    private val inputController: InputController,
    private val uiController: UIController,
    private val scope: CoroutineScope
) {
    companion object {
        private const val DEFAULT_GATEWAY_HOST = "127.0.0.1"
        private const val DEFAULT_GATEWAY_PORT = 18789
        private const val NODE_DISPLAY_NAME = "Samsung A25 Enhanced"
        private const val NODE_VERSION = "1.0.0"
    }
    
    private var webSocketClient: WebSocketClient? = null
    private val json = Json { ignoreUnknownKeys = true }
    
    fun connect() {
        scope.launch(Dispatchers.IO) {
            try {
                val gatewayUrl = "ws://$DEFAULT_GATEWAY_HOST:$DEFAULT_GATEWAY_PORT"
                Timber.i("Connecting to OpenClaw Gateway: $gatewayUrl")
                
                val uri = URI(gatewayUrl)
                webSocketClient = createWebSocketClient(uri)
                webSocketClient?.connect()
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to connect to Gateway")
            }
        }
    }
    
    fun disconnect() {
        webSocketClient?.close()
        webSocketClient = null
        Timber.i("Disconnected from OpenClaw Gateway")
    }
    
    private fun createWebSocketClient(uri: URI): WebSocketClient {
        return object : WebSocketClient(uri) {
            override fun onOpen(handshake: ServerHandshake?) {
                Timber.i("WebSocket connection opened to Gateway")
                sendNodeRegistration()
            }
            
            override fun onMessage(message: String?) {
                message?.let { handleMessage(it) }
            }
            
            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Timber.w("WebSocket connection closed: $reason (code: $code, remote: $remote)")
                
                // Attempt to reconnect after a delay
                scope.launch {
                    kotlinx.coroutines.delay(5000)
                    if (webSocketClient == null) { // Only reconnect if not manually disconnected
                        connect()
                    }
                }
            }
            
            override fun onError(ex: Exception?) {
                Timber.e(ex, "WebSocket error")
            }
        }
    }
    
    private fun sendNodeRegistration() {
        val registrationMessage = JsonObject(mapOf(
            "type" to JsonPrimitive("node_register"),
            "payload" to JsonObject(mapOf(
                "nodeId" to JsonPrimitive(getNodeId()),
                "displayName" to JsonPrimitive(NODE_DISPLAY_NAME),
                "version" to JsonPrimitive(NODE_VERSION),
                "platform" to JsonPrimitive("android"),
                "capabilities" to JsonArray(listOf(
                    JsonPrimitive("app.launch"),
                    JsonPrimitive("app.list"),
                    JsonPrimitive("input.tap"),
                    JsonPrimitive("input.text"),
                    JsonPrimitive("input.key"),
                    JsonPrimitive("ui.findElement"),
                    JsonPrimitive("ui.clickElement"),
                    JsonPrimitive("ui.getScreenContent")
                ))
            ))
        ))
        
        sendMessage(registrationMessage)
        Timber.i("Sent node registration")
    }
    
    private fun handleMessage(message: String) {
        scope.launch {
            try {
                val jsonMessage = json.parseToJsonElement(message).jsonObject
                val type = jsonMessage["type"]?.jsonPrimitive?.content
                
                when (type) {
                    "node_command" -> handleNodeCommand(jsonMessage)
                    "ping" -> handlePing(jsonMessage)
                    else -> Timber.w("Unknown message type: $type")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to handle message: $message")
            }
        }
    }
    
    private suspend fun handleNodeCommand(message: JsonObject) {
        val payload = message["payload"]?.jsonObject ?: return
        val commandId = payload["id"]?.jsonPrimitive?.content ?: UUID.randomUUID().toString()
        val command = payload["command"]?.jsonPrimitive?.content ?: return
        val params = payload["params"]
        
        Timber.i("Handling command: $command (id: $commandId)")
        
        val result = when (command) {
            "app.launch" -> {
                val launchParams = if (params != null) {
                    json.decodeFromJsonElement<AppLaunchParams>(params)
                } else {
                    return sendErrorResponse(commandId, "Missing launch parameters")
                }
                appController.launchApp(launchParams)
            }
            
            "app.list" -> {
                appController.getInstalledApps()
            }
            
            "input.tap" -> {
                val tapParams = if (params != null) {
                    json.decodeFromJsonElement<InputTapParams>(params)
                } else {
                    return sendErrorResponse(commandId, "Missing tap parameters")
                }
                inputController.performTap(tapParams)
            }
            
            "input.text" -> {
                val textParams = if (params != null) {
                    json.decodeFromJsonElement<InputTextParams>(params)
                } else {
                    return sendErrorResponse(commandId, "Missing text parameters")
                }
                inputController.inputText(textParams)
            }
            
            "input.key" -> {
                val keyParams = if (params != null) {
                    json.decodeFromJsonElement<InputKeyParams>(params)
                } else {
                    return sendErrorResponse(commandId, "Missing key parameters")
                }
                inputController.sendKeyEvent(keyParams)
            }
            
            "ui.findElement" -> {
                val findParams = if (params != null) {
                    json.decodeFromJsonElement<FindElementParams>(params)
                } else {
                    return sendErrorResponse(commandId, "Missing find parameters")
                }
                uiController.findElement(findParams)
            }
            
            "ui.clickElement" -> {
                val description = params?.jsonObject?.get("description")?.jsonPrimitive?.content
                    ?: return sendErrorResponse(commandId, "Missing element description")
                uiController.clickElement(description)
            }
            
            "ui.getScreenContent" -> {
                uiController.getScreenContent()
            }
            
            else -> {
                JsonObject(mapOf(
                    "success" to JsonPrimitive(false),
                    "error" to JsonPrimitive("Unknown command: $command")
                ))
            }
        }
        
        sendCommandResponse(commandId, result)
    }
    
    private fun handlePing(message: JsonObject) {
        val payload = message["payload"]?.jsonObject ?: JsonObject(emptyMap())
        val pongMessage = JsonObject(mapOf(
            "type" to JsonPrimitive("pong"),
            "payload" to payload
        ))
        sendMessage(pongMessage)
    }
    
    private fun sendCommandResponse(commandId: String, result: JsonObject) {
        val response = JsonObject(mapOf(
            "type" to JsonPrimitive("node_response"),
            "payload" to JsonObject(mapOf(
                "id" to JsonPrimitive(commandId),
                "result" to result,
                "timestamp" to JsonPrimitive(System.currentTimeMillis())
            ))
        ))
        
        sendMessage(response)
    }
    
    private fun sendErrorResponse(commandId: String, error: String) {
        val response = JsonObject(mapOf(
            "type" to JsonPrimitive("node_response"),
            "payload" to JsonObject(mapOf(
                "id" to JsonPrimitive(commandId),
                "result" to JsonObject(mapOf(
                    "success" to JsonPrimitive(false),
                    "error" to JsonPrimitive(error)
                )),
                "timestamp" to JsonPrimitive(System.currentTimeMillis())
            ))
        ))
        
        sendMessage(response)
    }
    
    private fun sendMessage(message: JsonObject) {
        val messageString = json.encodeToString(message)
        webSocketClient?.send(messageString)
        Timber.d("Sent message: $messageString")
    }
    
    private fun getNodeId(): String {
        // Generate a unique node ID based on device characteristics
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
        return "enhanced-node-$androidId"
    }
}