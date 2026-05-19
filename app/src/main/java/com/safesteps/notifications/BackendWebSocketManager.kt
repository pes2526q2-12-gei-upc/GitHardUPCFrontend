package com.safesteps.notifications

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

private const val BackendWebSocketTag = "BACKEND_WS"
private const val BackendWebSocketEndpoint = "ws://nattech.fib.upc.edu:40381/ws-safesteps"
private const val BackendReconnectDelayMillis = 5_000L
private const val BackendStompNull = '\u0000'
private const val BackendLocationUpdateDestination = "/app/location.update"

private const val MessageTitleKey = "NEW_MESSAGE_TITLE"
private const val MessageBodyKey = "NEW_MESSAGE_BODY"
private const val EmergencyTitleKey = "EMERGENCY_TITLE"
private const val EmergencyBodyKey = "EMERGENCY_BODY"
private const val FriendRequestTitleKey = "FRIEND_REQ_TITLE"
private const val FriendRequestBodyKey = "FRIEND_REQ_BODY"

data class BackendLocationSocketEvent(
    val latitude: Double,
    val longitude: Double,
    val sourceKey: String? = null,
    val username: String? = null,
    val title: String? = null,
    val body: String? = null
)

data class BackendEmergencySocketEvent(
    val sourceKey: String? = null,
    val title: String? = null,
    val body: String? = null
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
    private var settingsObservationJob: Job? = null
    private var stompConnected = false
    private var intentionalDisconnect = false
    private var desiredChannelSettings = SocketChannelSettings()
    private var subscribedChannels = mutableSetOf<SocketChannelPreference>()
    private val _locationEvents = MutableSharedFlow<BackendLocationSocketEvent>(
        extraBufferCapacity = 16
    )
    private val _emergencyEvents = MutableSharedFlow<BackendEmergencySocketEvent>(
        extraBufferCapacity = 16
    )

    val locationEvents: SharedFlow<BackendLocationSocketEvent> = _locationEvents.asSharedFlow()
    val emergencyEvents: SharedFlow<BackendEmergencySocketEvent> = _emergencyEvents.asSharedFlow()

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

        observeChannelSettings(context.applicationContext)
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

    fun sendLocationUpdate(
        latitude: Double,
        longitude: Double
    ): Boolean {
        if (!latitude.isFinite() || !longitude.isFinite()) {
            return false
        }

        val socket = synchronized(stateLock) {
            if (!stompConnected) {
                null
            } else {
                webSocket
            }
        } ?: return false

        val body = JSONObject()
            .put("lat", latitude)
            .put("lon", longitude)
            .toString()

        return socket.send(
            buildSendFrame(
                destination = BackendLocationUpdateDestination,
                body = body,
                contentType = "application/json"
            )
        )
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
        subscribedChannels.clear()
        return currentSocket
    }

    private fun observeChannelSettings(context: Context) {
        synchronized(stateLock) {
            if (settingsObservationJob != null) {
                return
            }

            settingsObservationJob = scope.launch {
                SocketChannelPreferences.settings(context).collectLatest { settings ->
                    updateChannelSettings(settings)
                }
            }
        }
    }

    private fun updateChannelSettings(settings: SocketChannelSettings) {
        val currentSocket = synchronized(stateLock) {
            desiredChannelSettings = settings
            if (!stompConnected) {
                null
            } else {
                webSocket
            }
        } ?: return

        applyChannelSubscriptions(currentSocket)
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

        applyChannelSubscriptions(currentSocket)
        Log.d(BackendWebSocketTag, "Suscripciones websocket activas para $googleId")
    }

    private fun handleBackendFrame(
        socket: WebSocket,
        frame: StompFrame
    ) {
        val destination = frame.headers["destination"]
        val channel = SocketChannelPreference.fromDestination(destination)
        val isCurrentSocket = synchronized(stateLock) {
            webSocket == socket &&
                stompConnected &&
                channel != null &&
                desiredChannelSettings.isEnabled(channel)
        }
        if (!isCurrentSocket) {
            return
        }

        val context = appContext ?: return
        val payload = BackendPayload.parse(frame.body)

        when (channel) {
            SocketChannelPreference.MESSAGES -> {
                showIncomingMessageNotification(
                    context = context,
                    title = payload.resolveTitle(MessageTitleKey),
                    body = payload.resolveBody(MessageBodyKey)
                )
            }

            SocketChannelPreference.EMERGENCY -> {
                Log.d(BackendWebSocketTag, "Notificación de EMERGENCIA recibida (raw): ${frame.body}")

                val resolvedTitle = payload.resolveTitle(EmergencyTitleKey)
                val resolvedBody = payload.resolveBody(EmergencyBodyKey)
                val resolvedUsername = payload.root?.optString("data")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: payload.resolveUsername()

                showIncomingEmergencyNotification(
                    context = context,
                    title = resolvedTitle,
                    body = resolvedBody,
                    senderName = resolvedUsername,
                    titleKey = payload.resolveTitleKey(),
                    bodyKey = payload.resolveBodyKey()
                )
                _emergencyEvents.tryEmit(
                    BackendEmergencySocketEvent(
                        sourceKey = payload.resolveSourceKey(),
                        title = resolvedTitle,
                        body = resolvedBody
                    )
                )
            }

            SocketChannelPreference.FRIEND_REQUESTS -> {
                val resolvedTitle = payload.resolveTitle(FriendRequestTitleKey)
                val resolvedBody = payload.resolveBody(FriendRequestBodyKey)
                showIncomingFriendRequestNotification(
                    context = context,
                    title = resolvedTitle,
                    body = resolvedBody,
                    senderName = payload.resolveFriendRequestActorName(),
                    status = payload.resolveFriendRequestStatus()
                )
            }

            SocketChannelPreference.LOCATION -> {
                val coordinates = payload.extractCoordinates()
                val resolvedUsername = payload.resolveUsername()
                val resolvedTitle = payload.resolveTitle(null)
                val resolvedBody = payload.resolveBody(null)
                coordinates?.let {
                    _locationEvents.tryEmit(
                        BackendLocationSocketEvent(
                            latitude = it.latitude,
                            longitude = it.longitude,
                            sourceKey = payload.resolveSourceKey(),
                            username = resolvedUsername,
                            title = resolvedTitle,
                            body = resolvedBody
                        )
                    )
                }
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

    private fun applyChannelSubscriptions(socket: WebSocket) {
        val operations = synchronized(stateLock) {
            if (webSocket != socket || !stompConnected) {
                return
            }

            val desiredChannels = desiredChannelSettings.enabledChannels()
            val channelsToSubscribe = desiredChannels - subscribedChannels
            val channelsToUnsubscribe = subscribedChannels - desiredChannels

            subscribedChannels.removeAll(channelsToUnsubscribe)
            subscribedChannels.addAll(channelsToSubscribe)

            SubscriptionOperations(
                channelsToSubscribe = channelsToSubscribe,
                channelsToUnsubscribe = channelsToUnsubscribe
            )
        }

        operations.channelsToUnsubscribe.forEach { channel ->
            socket.send(buildUnsubscribeFrame(channel.subscriptionId))
        }
        operations.channelsToSubscribe.forEach { channel ->
            socket.send(
                buildSubscribeFrame(
                    destination = channel.destination,
                    subscriptionId = channel.subscriptionId
                )
            )
        }
    }

    private fun buildConnectFrame(googleId: String): String {
        return buildString {
            append("CONNECT\n")
            append("accept-version:1.2\n")
            append("host:nattech.fib.upc.edu:40381\n")
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

    private fun buildSendFrame(
        destination: String,
        body: String,
        contentType: String? = null
    ): String {
        return buildString {
            append("SEND\n")
            append("destination:$destination\n")
            contentType?.let { append("content-type:$it\n") }
            append("content-length:${body.toByteArray(Charsets.UTF_8).size}\n")
            append("\n")
            append(body)
            append(BackendStompNull)
        }
    }

    private fun buildUnsubscribeFrame(subscriptionId: String): String {
        return buildString {
            append("UNSUBSCRIBE\n")
            append("id:$subscriptionId\n")
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

    private data class SubscriptionOperations(
        val channelsToSubscribe: Set<SocketChannelPreference>,
        val channelsToUnsubscribe: Set<SocketChannelPreference>
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

        fun resolveTitleKey(): String? {
            return root?.optString("titleKey")
                ?.takeIf { it.isMeaningfulPayloadText() }
        }

        fun resolveBodyKey(): String? {
            return root?.optString("bodyKey")
                ?.takeIf { it.isMeaningfulPayloadText() }
        }

        fun resolveTitle(defaultKey: String?): String? {
            val directTitle = sequenceOf(root, dataObject)
                .mapNotNull { objectNode ->
                    objectNode?.optString("title")
                        ?.takeIf { it.isMeaningfulPayloadText() }
                }
                .firstOrNull()
            if (directTitle != null) {
                return directTitle
            }

            val titleKey = resolveTitleKey()
                ?: return null

            return titleKey.takeUnless { it == defaultKey }
        }

        fun resolveBody(defaultKey: String?): String? {
            val directBody = sequenceOf(root, dataObject)
                .mapNotNull { objectNode ->
                    objectNode?.optString("body")
                        ?.takeIf { it.isMeaningfulPayloadText() }
                }
                .firstOrNull()
            if (directBody != null) {
                return directBody
            }

            val bodyKey = resolveBodyKey()
                ?: return null

            return bodyKey.takeUnless { it == defaultKey }
        }

        fun resolveSourceKey(): String? {
            val candidateKeys = listOf(
                "googleId",
                "userId",
                "senderGoogleId",
                "contactGoogleId",
                "username"
            )

            return sequenceOf(dataObject, root)
                .flatMap { objectNode ->
                    candidateKeys.asSequence().mapNotNull { key ->
                        objectNode?.optString(key)
                            ?.takeIf { it.isMeaningfulPayloadText() }
                    }
                }
                .firstOrNull()
        }

        fun resolveUsername(): String? {
            val candidateKeys = listOf(
                "username",
                "userName",
                "displayName",
                "name"
            )

            return sequenceOf(
                dataObject,
                dataObject?.optJSONObject("payload"),
                root,
                root?.optJSONObject("payload")
            )
                .flatMap { objectNode ->
                    candidateKeys.asSequence().mapNotNull { key ->
                        objectNode?.optString(key)
                            ?.takeIf { it.isMeaningfulPayloadText() }
                    }
                }
                .firstOrNull()
        }

        fun resolveFriendRequestActorName(): String? {
            val candidateKeys = listOf(
                "fromUser",
                "fromUsername",
                "fromDisplayName",
                "senderName",
                "senderUsername",
                "username",
                "userName",
                "displayName",
                "name"
            )

            return sequenceOf(
                dataObject,
                dataObject?.optJSONObject("payload"),
                root,
                root?.optJSONObject("payload")
            )
                .flatMap { objectNode ->
                    candidateKeys.asSequence().mapNotNull { key ->
                        objectNode?.optString(key)
                            ?.takeIf { it.isMeaningfulPayloadText() }
                    }
                }
                .firstOrNull()
        }

        fun resolveFriendRequestStatus(): String? {
            val candidateKeys = listOf(
                "status",
                "friendshipStatus",
                "requestStatus"
            )

            return sequenceOf(
                dataObject,
                dataObject?.optJSONObject("payload"),
                root,
                root?.optJSONObject("payload")
            )
                .flatMap { objectNode ->
                    candidateKeys.asSequence().mapNotNull { key ->
                        objectNode?.optString(key)
                            ?.takeIf { it.isMeaningfulPayloadText() }
                    }
                }
                .firstOrNull()
        }

        fun extractCoordinates(): Coordinates? {
            val candidates = candidateObjects(dataObject) + candidateObjects(root)
            for (candidate in candidates) {
                parseCoordinates(candidate)?.let { return it }
            }

            return null
        }

        private fun candidateObjects(objectNode: JSONObject?): List<JSONObject> {
            if (objectNode == null) {
                return emptyList()
            }

            val nestedKeys = listOf("coord", "coords", "coordinates", "location", "payload")
            val locationKeys = listOf("coord", "coords", "coordinates", "location")

            return buildList {
                add(objectNode)
                nestedKeys.forEach { key ->
                    objectNode.optJSONObject(key)?.let { nested ->
                        add(nested)
                        locationKeys.forEach { nestedLocationKey ->
                            nested.optJSONObject(nestedLocationKey)?.let(::add)
                        }
                    }
                }
            }
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

    private fun String.isMeaningfulPayloadText(): Boolean {
        return isNotBlank() && !equals("null", ignoreCase = true)
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
