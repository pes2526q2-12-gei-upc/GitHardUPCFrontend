package com.safesteps.notifications

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

private const val EmergencyWebSocketTag = "EMERGENCY_WS"
private const val EmergencyWebSocketEndpoint = "ws://nattech.fib.upc.edu:40385/ws-safesteps"
private const val EmergencyDestination = "/user/queue/emergency"
private const val EmergencySubscriptionId = "emergency-subscription"
private const val EmergencyReconnectDelayMillis = 5_000L
private const val EmergencyStompNull = '\u0000'

object EmergencyWebSocketManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .build()

    private val stateLock = Any()

    private var appContext: Context? = null
    private var desiredGoogleId: String? = null
    private var activeGoogleId: String? = null
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var stompConnected = false
    private var intentionalDisconnect = false

    fun connect(
        context: Context,
        googleId: String
    ) {
        val normalizedGoogleId = googleId.trim()
        if (normalizedGoogleId.isBlank()) {
            return
        }

        var socketToClose: WebSocket? = null
        synchronized(stateLock) {
            val shouldReuseConnection =
                desiredGoogleId == normalizedGoogleId &&
                    activeGoogleId == normalizedGoogleId &&
                    webSocket != null
            if (shouldReuseConnection) {
                return
            }

            appContext = context.applicationContext
            desiredGoogleId = normalizedGoogleId
            intentionalDisconnect = false
            reconnectJob?.cancel()
            reconnectJob = null
            socketToClose = clearCurrentSocketLocked()
        }

        socketToClose?.close(1000, "Switching emergency websocket session")
        openSocket(normalizedGoogleId)
    }

    fun disconnect() {
        var socketToClose: WebSocket? = null
        synchronized(stateLock) {
            desiredGoogleId = null
            intentionalDisconnect = true
            reconnectJob?.cancel()
            reconnectJob = null
            socketToClose = clearCurrentSocketLocked()
        }

        socketToClose?.apply {
            send(buildDisconnectFrame())
            close(1000, "Emergency websocket disconnected")
        }
    }

    private fun openSocket(googleId: String) {
        val request = Request.Builder()
            .url(buildEmergencySocketUrl(googleId))
            .addHeader("Sec-WebSocket-Protocol", "v12.stomp")
            .build()

        val socket = client.newWebSocket(request, EmergencySocketListener(googleId))
        synchronized(stateLock) {
            activeGoogleId = googleId
            webSocket = socket
            stompConnected = false
        }
        Log.d(EmergencyWebSocketTag, "Abriendo websocket de emergencia para $googleId")
    }

    private fun buildEmergencySocketUrl(googleId: String): String {
        return "$EmergencyWebSocketEndpoint?googleId=${Uri.encode(googleId)}"
    }

    private fun clearCurrentSocketLocked(): WebSocket? {
        val currentSocket = webSocket
        webSocket = null
        activeGoogleId = null
        stompConnected = false
        return currentSocket
    }

    private fun scheduleReconnect(googleId: String) {
        synchronized(stateLock) {
            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(EmergencyReconnectDelayMillis)

                val shouldReconnect = synchronized(stateLock) {
                    !intentionalDisconnect &&
                        desiredGoogleId == googleId &&
                        webSocket == null
                }

                if (!shouldReconnect) {
                    return@launch
                }

                Log.d(EmergencyWebSocketTag, "Reintentando websocket de emergencia para $googleId")
                openSocket(googleId)
            }
        }
    }

    private fun onSocketReady(
        socket: WebSocket,
        googleId: String
    ) {
        val isCurrentSocket = synchronized(stateLock) {
            webSocket == socket && activeGoogleId == googleId
        }
        if (!isCurrentSocket) {
            socket.close(1000, "Ignoring stale emergency websocket")
            return
        }

        socket.send(buildConnectFrame(googleId))
        Log.d(EmergencyWebSocketTag, "Handshake websocket abierto para $googleId")
    }

    private fun onSocketMessage(
        socket: WebSocket,
        googleId: String,
        message: String
    ) {
        parseStompFrames(message).forEach { frame ->
            when (frame.command) {
                "CONNECTED" -> handleConnectedFrame(socket, googleId)
                "MESSAGE" -> handleEmergencyFrame(socket, frame)
                "ERROR" -> Log.e(EmergencyWebSocketTag, "Frame STOMP ERROR para $googleId: ${frame.body}")
            }
        }
    }

    private fun handleConnectedFrame(
        socket: WebSocket,
        googleId: String
    ) {
        val currentSocket = synchronized(stateLock) {
            if (desiredGoogleId != googleId || activeGoogleId != googleId || webSocket != socket) {
                return
            }
            stompConnected = true
            webSocket
        } ?: return

        currentSocket.send(buildSubscribeFrame())
        Log.d(EmergencyWebSocketTag, "Suscrito a $EmergencyDestination para $googleId")
    }

    private fun handleEmergencyFrame(
        socket: WebSocket,
        frame: StompFrame
    ) {
        val isCurrentSocket = synchronized(stateLock) {
            webSocket == socket && stompConnected
        }
        if (!isCurrentSocket) {
            return
        }

        val context = appContext ?: return
        val payload = parsePayload(frame.body)
        val title = payload.resolveTitle()
        val body = payload.resolveBody()

        Log.d(
            EmergencyWebSocketTag,
            "Notificacion de emergencia recibida en ${frame.headers["destination"] ?: EmergencyDestination}"
        )

        showIncomingEmergencyNotification(
            context = context,
            title = title,
            body = body
        )
    }

    private fun parsePayload(rawBody: String): JSONObject? {
        val normalizedBody = rawBody.trim()
        if (normalizedBody.isBlank()) {
            return null
        }

        return runCatching { JSONObject(normalizedBody) }
            .onFailure { error ->
                Log.w(EmergencyWebSocketTag, "No se pudo parsear el payload de emergencia", error)
            }
            .getOrNull()
    }

    private fun JSONObject?.resolveTitle(): String? {
        val directTitle = this?.optString("title")
            ?.takeIf { it.isNotBlank() }
        if (directTitle != null) {
            return directTitle
        }

        return this?.optString("titleKey")
            ?.takeIf { it.isNotBlank() && it != "EMERGENCY_TITLE" }
    }

    private fun JSONObject?.resolveBody(): String? {
        val directBody = this?.optString("body")
            ?.takeIf { it.isNotBlank() }
        if (directBody != null) {
            return directBody
        }

        return this?.optString("bodyKey")
            ?.takeIf { it.isNotBlank() && it != "EMERGENCY_BODY" }
    }

    private fun handleSocketEnded(
        socket: WebSocket,
        googleId: String,
        reason: String
    ) {
        val shouldReconnect = synchronized(stateLock) {
            if (webSocket != socket || activeGoogleId != googleId) {
                return
            }

            clearCurrentSocketLocked()
            !intentionalDisconnect && desiredGoogleId == googleId
        }

        Log.d(EmergencyWebSocketTag, reason)

        if (shouldReconnect) {
            scheduleReconnect(googleId)
        }
    }

    private fun buildConnectFrame(googleId: String): String {
        return buildString {
            append("CONNECT\n")
            append("accept-version:1.2\n")
            append("host:nattech.fib.upc.edu:40385\n")
            append("heart-beat:0,0\n")
            append("googleId:$googleId\n")
            append("\n")
            append(EmergencyStompNull)
        }
    }

    private fun buildSubscribeFrame(): String {
        return buildString {
            append("SUBSCRIBE\n")
            append("id:$EmergencySubscriptionId\n")
            append("destination:$EmergencyDestination\n")
            append("ack:auto\n")
            append("\n")
            append(EmergencyStompNull)
        }
    }

    private fun buildDisconnectFrame(): String {
        return buildString {
            append("DISCONNECT\n")
            append("\n")
            append(EmergencyStompNull)
        }
    }

    private fun parseStompFrames(rawMessage: String): List<StompFrame> {
        return rawMessage
            .split(EmergencyStompNull)
            .mapNotNull { chunk ->
                val normalizedChunk = chunk.trim('\n', '\r')
                if (normalizedChunk.isBlank()) {
                    null
                } else {
                    parseStompFrame(normalizedChunk)
                }
            }
    }

    private fun parseStompFrame(rawFrame: String): StompFrame? {
        val lines = rawFrame.split('\n')
        val command = lines.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val headers = mutableMapOf<String, String>()
        var lineIndex = 1

        while (lineIndex < lines.size) {
            val headerLine = lines[lineIndex].trimEnd('\r')
            lineIndex += 1

            if (headerLine.isEmpty()) {
                break
            }

            val separatorIndex = headerLine.indexOf(':')
            if (separatorIndex <= 0) {
                continue
            }

            val key = headerLine.substring(0, separatorIndex)
            val value = headerLine.substring(separatorIndex + 1)
            headers[key] = value
        }

        val body = lines
            .drop(lineIndex)
            .joinToString(separator = "\n")
            .trimEnd('\r')

        return StompFrame(
            command = command,
            headers = headers,
            body = body
        )
    }

    private data class StompFrame(
        val command: String,
        val headers: Map<String, String>,
        val body: String
    )

    private class EmergencySocketListener(
        private val googleId: String
    ) : WebSocketListener() {
        override fun onOpen(
            webSocket: WebSocket,
            response: Response
        ) {
            EmergencyWebSocketManager.onSocketReady(webSocket, googleId)
        }

        override fun onMessage(
            webSocket: WebSocket,
            text: String
        ) {
            EmergencyWebSocketManager.onSocketMessage(webSocket, googleId, text)
        }

        override fun onClosed(
            webSocket: WebSocket,
            code: Int,
            reason: String
        ) {
            EmergencyWebSocketManager.handleSocketEnded(
                socket = webSocket,
                googleId = googleId,
                reason = "Websocket de emergencia cerrado ($code): $reason"
            )
        }

        override fun onFailure(
            webSocket: WebSocket,
            t: Throwable,
            response: Response?
        ) {
            val failureReason = buildString {
                append("Fallo en websocket de emergencia para ")
                append(googleId)
                append(": ")
                append(t.message ?: "sin detalle")
                response?.let {
                    append(" (HTTP ")
                    append(it.code)
                    append(')')
                }
            }

            Log.e(EmergencyWebSocketTag, failureReason, t)
            EmergencyWebSocketManager.handleSocketEnded(webSocket, googleId, failureReason)
        }
    }
}
