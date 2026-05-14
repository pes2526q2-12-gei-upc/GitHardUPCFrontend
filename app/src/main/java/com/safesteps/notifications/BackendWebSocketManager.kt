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

private const val BackendWebSocketTag = "BACKEND_WS"
private const val BackendWebSocketEndpoint = "ws://nattech.fib.upc.edu:40385/ws-safesteps"
private const val BackendReconnectDelayMillis = 5_000L
private const val BackendStompNull = '\u0000'

private const val MessageDestination = "/user/queue/messages"
private const val EmergencyDestination = "/user/queue/emergency"
private const val FriendRequestDestination = "/user/queue/requests"
private const val LocationDestination = "/user/queue/location"

private const val MessageTitleKey = "NEW_MESSAGE_TITLE"
private const val MessageBodyKey = "NEW_MESSAGE_BODY"
private const val EmergencyTitleKey = "EMERGENCY_TITLE"
private const val EmergencyBodyKey = "EMERGENCY_BODY"
private const val FriendRequestTitleKey = "FRIEND_REQ_TITLE"
private const val FriendRequestBodyKey = "FRIEND_REQ_BODY"

private val BackendSubscriptions = linkedMapOf(
    MessageDestination to "messages-subscription",
    EmergencyDestination to "emergency-subscription",
    FriendRequestDestination to "friend-requests-subscription",
    LocationDestination to "location-subscription"
)

object BackendWebSocketManager {
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

        socketToClose?.close(1000, "Switching backend websocket session")
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
            close(1000, "Backend websocket disconnected")
        }
    }

    private fun openSocket(googleId: String) {
        val request = Request.Builder()
            .url(buildSocketUrl(googleId))
            .addHeader("Sec-WebSocket-Protocol", "v12.stomp")
            .build()

        val socket = client.newWebSocket(request, BackendSocketListener(googleId))
        synchronized(stateLock) {
            activeGoogleId = googleId
            webSocket = socket
            stompConnected = false
        }
        Log.d(BackendWebSocketTag, "Abriendo websocket backend para $googleId")
    }

    private fun buildSocketUrl(googleId: String): String {
        return "$BackendWebSocketEndpoint?googleId=${Uri.encode(googleId)}"
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
                delay(BackendReconnectDelayMillis)

                val shouldReconnect = synchronized(stateLock) {
                    !intentionalDisconnect &&
                        desiredGoogleId == googleId &&
                        webSocket == null
                }

                if (!shouldReconnect) {
                    return@launch
                }

                Log.d(BackendWebSocketTag, "Reintentando websocket backend para $googleId")
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
            socket.close(1000, "Ignoring stale backend websocket")
            return
        }

        socket.send(buildConnectFrame(googleId))
        Log.d(BackendWebSocketTag, "Handshake websocket abierto para $googleId")
    }

    private fun onSocketMessage(
        socket: WebSocket,
        googleId: String,
        message: String
    ) {
        parseStompFrames(message).forEach { frame ->
            when (frame.command) {
                "CONNECTED" -> handleConnectedFrame(socket, googleId)
                "MESSAGE" -> handleBackendFrame(socket, frame)
                "ERROR" -> Log.e(BackendWebSocketTag, "Frame STOMP ERROR para $googleId: ${frame.body}")
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

        BackendSubscriptions.forEach { (destination, subscriptionId) ->
            currentSocket.send(
                buildSubscribeFrame(
                    destination = destination,
                    subscriptionId = subscriptionId
                )
            )
        }
        Log.d(BackendWebSocketTag, "Suscripciones websocket activas para $googleId")
    }

    private fun handleBackendFrame(
        socket: WebSocket,
        frame: StompFrame
    ) {
        val isCurrentSocket = synchronized(stateLock) {
            webSocket == socket && stompConnected
        }
        if (!isCurrentSocket) {
            return
        }

        val destination = frame.headers["destination"]
        val context = appContext ?: return
        val payload = BackendPayload.parse(frame.body)

        when (destination) {
            MessageDestination -> {
                showIncomingMessageNotification(
                    context = context,
                    title = payload.resolveTitle(MessageTitleKey),
                    body = payload.resolveBody(MessageBodyKey)
                )
            }

            EmergencyDestination -> {
                showIncomingEmergencyNotification(
                    context = context,
                    title = payload.resolveTitle(EmergencyTitleKey),
                    body = payload.resolveBody(EmergencyBodyKey)
                )
            }

            FriendRequestDestination -> {
                showIncomingFriendRequestNotification(
                    context = context,
                    title = payload.resolveTitle(FriendRequestTitleKey),
                    body = payload.resolveBody(FriendRequestBodyKey)
                )
            }

            LocationDestination -> {
                val coordinates = payload.extractCoordinates()
                showIncomingLocationNotification(
                    context = context,
                    title = payload.resolveTitle(null),
                    body = payload.resolveBody(null),
                    latitude = coordinates?.latitude,
                    longitude = coordinates?.longitude
                )
            }

            else -> {
                Log.w(
                    BackendWebSocketTag,
                    "Destino websocket no gestionado: ${destination ?: "sin destino"}"
                )
                return
            }
        }

        Log.d(
            BackendWebSocketTag,
            "Evento websocket recibido en ${destination ?: "unknown"}"
        )
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

        Log.d(BackendWebSocketTag, reason)

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
            append(BackendStompNull)
        }
    }

    private fun buildSubscribeFrame(
        destination: String,
        subscriptionId: String
    ): String {
        return buildString {
            append("SUBSCRIBE\n")
            append("id:$subscriptionId\n")
            append("destination:$destination\n")
            append("ack:auto\n")
            append("\n")
            append(BackendStompNull)
        }
    }

    private fun buildDisconnectFrame(): String {
        return buildString {
            append("DISCONNECT\n")
            append("\n")
            append(BackendStompNull)
        }
    }

    private fun parseStompFrames(rawMessage: String): List<StompFrame> {
        return rawMessage
            .split(BackendStompNull)
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

    private data class Coordinates(
        val latitude: Double,
        val longitude: Double
    )

    private data class BackendPayload(
        val root: JSONObject?
    ) {
        private val dataObject: JSONObject?
            get() = root?.optJSONObject("data")

        fun resolveTitle(defaultKey: String?): String? {
            val directTitle = sequenceOf(root, dataObject)
                .mapNotNull { objectNode ->
                    objectNode?.optString("title")
                        ?.takeIf { it.isNotBlank() }
                }
                .firstOrNull()
            if (directTitle != null) {
                return directTitle
            }

            val titleKey = root?.optString("titleKey")
                ?.takeIf { it.isNotBlank() }
                ?: return null

            return titleKey.takeUnless { it == defaultKey }
        }

        fun resolveBody(defaultKey: String?): String? {
            val directBody = sequenceOf(root, dataObject)
                .mapNotNull { objectNode ->
                    objectNode?.optString("body")
                        ?.takeIf { it.isNotBlank() }
                }
                .firstOrNull()
            if (directBody != null) {
                return directBody
            }

            val bodyKey = root?.optString("bodyKey")
                ?.takeIf { it.isNotBlank() }
                ?: return null

            return bodyKey.takeUnless { it == defaultKey }
        }

        fun extractCoordinates(): Coordinates? {
            val candidates = listOfNotNull(
                dataObject,
                root?.optJSONObject("coords"),
                root?.optJSONObject("location"),
                root
            )

            for (candidate in candidates) {
                parseCoordinates(candidate)?.let { return it }
            }

            return null
        }

        private fun parseCoordinates(objectNode: JSONObject): Coordinates? {
            return listOf(
                "lat" to "lon",
                "lat" to "lng",
                "latitude" to "longitude"
            ).firstNotNullOfOrNull { (latitudeKey, longitudeKey) ->
                val latitude = objectNode.optDouble(latitudeKey, Double.NaN)
                val longitude = objectNode.optDouble(longitudeKey, Double.NaN)
                if (latitude.isNaN() || longitude.isNaN()) {
                    null
                } else {
                    Coordinates(latitude = latitude, longitude = longitude)
                }
            }
        }

        companion object {
            fun parse(rawBody: String): BackendPayload {
                val normalizedBody = rawBody.trim()
                if (normalizedBody.isBlank()) {
                    return BackendPayload(root = null)
                }

                val jsonObject = runCatching { JSONObject(normalizedBody) }
                    .onFailure { error ->
                        Log.w(BackendWebSocketTag, "No se pudo parsear el payload websocket", error)
                    }
                    .getOrNull()

                return BackendPayload(root = jsonObject)
            }
        }
    }

    private class BackendSocketListener(
        private val googleId: String
    ) : WebSocketListener() {
        override fun onOpen(
            webSocket: WebSocket,
            response: Response
        ) {
            BackendWebSocketManager.onSocketReady(webSocket, googleId)
        }

        override fun onMessage(
            webSocket: WebSocket,
            text: String
        ) {
            BackendWebSocketManager.onSocketMessage(webSocket, googleId, text)
        }

        override fun onClosed(
            webSocket: WebSocket,
            code: Int,
            reason: String
        ) {
            BackendWebSocketManager.handleSocketEnded(
                socket = webSocket,
                googleId = googleId,
                reason = "Websocket backend cerrado ($code): $reason"
            )
        }

        override fun onFailure(
            webSocket: WebSocket,
            t: Throwable,
            response: Response?
        ) {
            val failureReason = buildString {
                append("Fallo en websocket backend para ")
                append(googleId)
                append(": ")
                append(t.message ?: "sin detalle")
                response?.let {
                    append(" (HTTP ")
                    append(it.code)
                    append(')')
                }
            }

            Log.e(BackendWebSocketTag, failureReason, t)
            BackendWebSocketManager.handleSocketEnded(webSocket, googleId, failureReason)
        }
    }
}
