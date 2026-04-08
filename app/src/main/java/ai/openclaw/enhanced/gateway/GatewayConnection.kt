// ===========================================================================
// GatewayConnection.kt
// ai/openclaw/enhanced/gateway/GatewayConnection.kt
//
// Manages the WebSocket connection to the OpenClaw gateway.
// Implements the v3 protocol: challenge -> Ed25519 sign -> connect -> handle
// commands via node.invoke.request / node.invoke.result frames.
// ===========================================================================

package ai.openclaw.enhanced.gateway

import android.content.Context
import android.os.Build
import ai.openclaw.enhanced.controller.AppController
import ai.openclaw.enhanced.controller.InputController
import ai.openclaw.enhanced.controller.UIController
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
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import java.security.KeyStore

class GatewayConnection(
    private val context: Context,
    private val appController: AppController,
    private val inputController: InputController,
    private val uiController: UIController,
    private val scope: CoroutineScope
) {
    companion object {
        private const val GATEWAY_HOST = "superhuman-vps.zebra-trench.ts.net"
        private const val GATEWAY_PORT = 18789
        private const val GATEWAY_TOKEN = "179b5c4b69db4bc4d39fa87afc86896267c42de4ef929642"
        private const val USE_TLS = true
        private const val CLIENT_ID = "openclaw-android"
        private const val CLIENT_VERSION = "1.0.0"
        private const val DISPLAY_NAME = "Samsung A25 Enhanced"
        private const val PROTOCOL_VERSION = 3
        private const val RECONNECT_DELAY_MS = 5000L
    }

    private var webSocketClient: WebSocketClient? = null
    private val json = Json { ignoreUnknownKeys = true }
    private val identityStore = DeviceIdentityStore(context)
    private val dispatcher = CommandDispatcher(appController, inputController, uiController)

    // The node ID is our device identity (SHA-256 hex of Ed25519 public key)
    private val nodeId: String = identityStore.getDeviceId()

    fun connect() {
        scope.launch(Dispatchers.IO) {
            try {
                val protocol = if (USE_TLS) "wss" else "ws"
                val url = "$protocol://$GATEWAY_HOST:$GATEWAY_PORT/"
                Timber.i("Connecting to OpenClaw Gateway: $url")

                val uri = URI(url)
                webSocketClient = createWebSocketClient(uri)

                if (USE_TLS) {
                    val sslContext = SSLContext.getInstance("TLS")
                    val tmf = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm()
                    )
                    tmf.init(null as KeyStore?)
                    sslContext.init(null, tmf.trustManagers, null)
                    webSocketClient?.setSocketFactory(sslContext.socketFactory)
                }

                webSocketClient?.connect()
            } catch (e: Exception) {
                Timber.e(e, "Failed to connect to Gateway")
            }
        }
    }

    fun disconnect() {
        val client = webSocketClient
        webSocketClient = null // Mark as intentionally disconnected
        client?.close()
        Timber.i("Disconnected from OpenClaw Gateway")
    }

    // -----------------------------------------------------------------------
    // WebSocket lifecycle
    // -----------------------------------------------------------------------

    private fun createWebSocketClient(uri: URI): WebSocketClient {
        return object : WebSocketClient(uri) {
            override fun onOpen(handshake: ServerHandshake?) {
                Timber.i("WebSocket opened, waiting for challenge...")
            }

            override fun onMessage(message: String?) {
                message?.let { handleRawMessage(it) }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Timber.w("WebSocket closed: $reason (code=$code, remote=$remote)")
                scheduleReconnect()
            }

            override fun onError(ex: Exception?) {
                Timber.e(ex, "WebSocket error")
            }
        }
    }

    private fun scheduleReconnect() {
        scope.launch {
            kotlinx.coroutines.delay(RECONNECT_DELAY_MS)
            // Only reconnect if not intentionally disconnected
            if (webSocketClient != null) {
                Timber.i("Attempting reconnect...")
                connect()
            }
        }
    }

    // -----------------------------------------------------------------------
    // Message routing
    // -----------------------------------------------------------------------

    private fun handleRawMessage(raw: String) {
        scope.launch {
            try {
                val msg = json.parseToJsonElement(raw).jsonObject
                val type = msg["type"]?.jsonPrimitive?.content

                when (type) {
                    "event" -> handleEvent(msg)
                    "res" -> handleResponse(msg)
                    else -> Timber.w("Unknown frame type: $type")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to handle message: $raw")
            }
        }
    }

    private suspend fun handleEvent(msg: JsonObject) {
        val event = msg["event"]?.jsonPrimitive?.content ?: return

        when (event) {
            "connect.challenge" -> handleChallenge(msg)
            "node.invoke.request" -> handleInvokeRequest(msg)
            "tick" -> handleTick(msg)
            else -> Timber.d("Unhandled event: $event")
        }
    }

    private fun handleResponse(msg: JsonObject) {
        val id = msg["id"]?.jsonPrimitive?.content
        val ok = msg["ok"]?.jsonPrimitive?.booleanOrNull ?: false
        if (ok) {
            Timber.i("Response OK for request $id")
        } else {
            // Error can be a JsonObject or JsonPrimitive
            val error = msg["error"]
            Timber.w("Response FAILED for request $id: $error")
        }
    }

    // -----------------------------------------------------------------------
    // Challenge -> Connect handshake (v3 protocol)
    // -----------------------------------------------------------------------

    private fun handleChallenge(msg: JsonObject) {
        val payload = msg["payload"]?.jsonObject ?: return
        val nonce = payload["nonce"]?.jsonPrimitive?.content ?: return
        Timber.i("Received challenge with nonce: $nonce")

        val signedAt = System.currentTimeMillis()
        val deviceId = identityStore.getDeviceId()

        // Build the v3 signature payload string:
        // v3|{deviceId}|openclaw-android|node|node||{signedAtMs}|{token}|{nonce}|android|android
        val sigPayload = "v3|$deviceId|$CLIENT_ID|node|node||$signedAt|$GATEWAY_TOKEN|$nonce|android|android"
        val signature = identityStore.sign(sigPayload)

        // Build the connect request frame
        val connectReq = buildJsonObject {
            put("type", "req")
            put("id", UUID.randomUUID().toString())
            put("method", "connect")
            putJsonObject("params") {
                put("minProtocol", PROTOCOL_VERSION)
                put("maxProtocol", PROTOCOL_VERSION)
                putJsonObject("client") {
                    put("id", CLIENT_ID)
                    put("displayName", DISPLAY_NAME)
                    put("version", CLIENT_VERSION)
                    put("platform", "android")
                    put("mode", "node")
                    put("instanceId", nodeId)
                    put("deviceFamily", "Android")
                    put("modelIdentifier", "${Build.MANUFACTURER} ${Build.MODEL}")
                }
                put("role", "node")
                putJsonArray("scopes") {}
                // All OpenClaw capability categories
                putJsonArray("caps") {
                    add("system"); add("canvas"); add("camera")
                    add("screen"); add("location"); add("sms")
                    add("device"); add("notifications"); add("contacts")
                    add("calendar"); add("photos"); add("motion")
                }
                putJsonArray("commands") {
                    // Canvas
                    add("canvas.snapshot"); add("canvas.present"); add("canvas.hide")
                    add("canvas.navigate"); add("canvas.eval")
                    add("canvas.a2ui.push"); add("canvas.a2ui.reset")
                    // Camera
                    add("camera.list"); add("camera.snap"); add("camera.clip")
                    // Screen
                    add("screen.record")
                    // System
                    add("system.run"); add("system.notify"); add("system.which")
                    add("system.execApprovals.get"); add("system.execApprovals.set")
                    // Location, SMS
                    add("location.get"); add("sms.send")
                    // Device & personal data (Android)
                    add("device.status"); add("device.info")
                    add("device.permissions"); add("device.health")
                    add("notifications.list"); add("notifications.actions")
                    add("photos.latest")
                    add("contacts.search"); add("contacts.add")
                    add("calendar.events"); add("calendar.add")
                    add("motion.activity"); add("motion.pedometer")
                    add("app.update")
                    // Custom enhanced UI automation commands
                    add("app.launch"); add("app.list")
                    add("input.tap"); add("input.text"); add("input.key")
                    add("ui.findElement"); add("ui.clickElement")
                    add("ui.getScreenContent")
                }
                putJsonObject("permissions") {}
                putJsonObject("auth") { put("token", GATEWAY_TOKEN) }
                putJsonObject("device") {
                    put("id", deviceId)
                    put("publicKey", identityStore.getPublicKeyBase64Url())
                    put("signature", signature)
                    put("signedAt", signedAt)
                    put("nonce", nonce)
                }
                put("locale", "en-US")
                put("userAgent", "$CLIENT_ID/$CLIENT_VERSION (Android ${Build.VERSION.RELEASE}; SDK ${Build.VERSION.SDK_INT})")
            }
        }

        sendFrame(connectReq)
        Timber.i("Sent connect request with signed challenge")
    }

    // -----------------------------------------------------------------------
    // Command handling: node.invoke.request -> node.invoke.result
    // -----------------------------------------------------------------------

    private suspend fun handleInvokeRequest(msg: JsonObject) {
        val payload = msg["payload"]?.jsonObject ?: return
        val invokeId = payload["id"]?.jsonPrimitive?.content ?: return
        val command = payload["command"]?.jsonPrimitive?.content ?: return
        val paramsJSON = payload["paramsJSON"]?.jsonPrimitive?.content

        Timber.i("Invoke request: $command (id=$invokeId)")

        // Dispatch the command to the appropriate controller
        val result = dispatcher.dispatch(command, paramsJSON)
        val ok = result["success"]?.jsonPrimitive?.booleanOrNull ?: false

        // Send the result back as a node.invoke.result request frame
        val resultFrame = buildJsonObject {
            put("type", "req")
            put("id", UUID.randomUUID().toString())
            put("method", "node.invoke.result")
            putJsonObject("params") {
                put("id", invokeId)
                put("nodeId", nodeId)
                put("ok", ok)
                put("payloadJSON", json.encodeToString(result))
            }
        }

        sendFrame(resultFrame)
        Timber.i("Sent invoke result for $command (ok=$ok)")
    }

    // -----------------------------------------------------------------------
    // Keepalive
    // -----------------------------------------------------------------------

    private fun handleTick(msg: JsonObject) {
        val ts = msg["payload"]?.jsonObject?.get("ts")?.jsonPrimitive?.longOrNull
        Timber.d("Tick received: ts=$ts")
    }

    // -----------------------------------------------------------------------
    // Sending
    // -----------------------------------------------------------------------

    private fun sendFrame(frame: JsonObject) {
        val raw = json.encodeToString(frame)
        webSocketClient?.send(raw)
        Timber.d("Sent frame: $raw")
    }
}
