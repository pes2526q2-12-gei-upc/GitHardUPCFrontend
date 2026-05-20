package com.safesteps.notifications

import android.content.Context
import android.net.Uri
import android.util.Log
import com.safesteps.R
import com.safesteps.chat.ChatEventBus
import com.safesteps.ui.notifications.ScreenNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

private const val BackendWebSocketTag = "BACKEND_WS"
private const val BackendWebSocketEndpoint = "ws://nattech.fib.upc.edu:40382/ws-safesteps"
private const val BackendReconnectDelayMillis = 5_000L
private const val BackendStompNull = '\u0000'

private const val MessageTitleKey = "NEW_MESSAGE_TITLE"
private const val MessageBodyKey = "NEW_MESSAGE_BODY"
private const val EmergencyTitleKey = "EMERGENCY_TITLE"
private const val EmergencyBodyKey = "EMERGENCY_BODY"
private const val FriendRequestTitleKey = "FRIEND_REQ_TITLE"
private const val FriendRequestBodyKey = "FRIEND_REQ_BODY"

object BackendWebSocketManager {
    private val alwaysSubscribedChannels = setOf(SocketChannelPreference.MESSAGES)
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
        val subscriptionId = frame.headers["subscription"]
        val channel = SocketChannelPreference.fromDestination(destination)
            ?: SocketChannelPreference.fromSubscriptionId(subscriptionId)
        if (channel == null) {
            Log.w(
                BackendWebSocketTag,
                "Frame websocket sin canal resoluble. destination=${destination ?: "none"} subscription=${subscriptionId ?: "none"}"
            )
            return
        }
        val settings = synchronized(stateLock) {
            if (webSocket != socket || !stompConnected) {
                return
            }
            desiredChannelSettings
        }

        val context = appContext ?: return
        val payload = BackendPayload.parse(frame.body)

        when (channel) {
            SocketChannelPreference.MESSAGES -> {
                val eventType = payload.extractEventType()
                val chatId = payload.extractChatId()
                val username = payload.extractUsername()
                when (eventType?.uppercase()) {
                    "PARTICIPANT_LEFT" -> ChatEventBus.onParticipantLeft(chatId, username)
                    "PARTICIPANT_JOINED" -> ChatEventBus.onParticipantJoined(chatId, username)
                    "PARTICIPANT_KICKED" -> ChatEventBus.onParticipantKicked(chatId, username)
                    "ADMIN_PROMOTED" -> ChatEventBus.onAdminPromoted(chatId, username)
                    "ADMIN_REVOKED" -> ChatEventBus.onAdminRevoked(chatId, username)
                    "GROUP_CREATED" -> ChatEventBus.onGroupCreated(chatId, payload.extractGroupName())
                    else -> ChatEventBus.onNewMessage(chatId)
                }
                if (settings.messagesEnabled) {
                    val title = payload.resolveTitle(MessageTitleKey)
                    val body = payload.resolveBody(MessageBodyKey)
                    val normalizedTitle = normalizeNotificationText(
                        rawValue = title,
                        knownKey = MessageTitleKey
                    )
                    val normalizedBody = normalizeNotificationText(
                        rawValue = body,
                        knownKey = MessageBodyKey
                    )
                    showIncomingMessageNotification(
                        context = context,
                        title = normalizedTitle,
                        body = normalizedBody
                    )
                    showMessageBanner(context, normalizedTitle, normalizedBody)
                }
            }

            SocketChannelPreference.EMERGENCY -> {
                if (!settings.emergencyEnabled) return
                val title = payload.resolveTitle(EmergencyTitleKey)
                val body = payload.resolveBody(EmergencyBodyKey)
                val normalizedTitle = normalizeNotificationText(
                    rawValue = title,
                    knownKey = EmergencyTitleKey
                )
                val normalizedBody = normalizeNotificationText(
                    rawValue = body,
                    knownKey = EmergencyBodyKey
                )
                showIncomingEmergencyNotification(
                    context = context,
                    title = normalizedTitle,
                    body = normalizedBody
                )
                showEmergencyBanner(context, normalizedTitle, normalizedBody)
            }

            SocketChannelPreference.FRIEND_REQUESTS -> {
                if (!settings.friendRequestsEnabled) return
                val title = payload.resolveTitle(FriendRequestTitleKey)
                val body = payload.resolveBody(FriendRequestBodyKey)
                val normalizedTitle = normalizeNotificationText(
                    rawValue = title,
                    knownKey = FriendRequestTitleKey
                )
                val normalizedBody = normalizeNotificationText(
                    rawValue = body,
                    knownKey = FriendRequestBodyKey
                )
                showIncomingFriendRequestNotification(
                    context = context,
                    title = normalizedTitle,
                    body = normalizedBody
                )
                showFriendRequestBanner(context, normalizedTitle, normalizedBody)
            }

            SocketChannelPreference.LOCATION -> {
                if (!settings.locationEnabled) return
                val coordinates = payload.extractCoordinates()
                val title = payload.resolveTitle(null)
                val body = payload.resolveBody(null)
                val normalizedTitle = normalizeNotificationText(
                    rawValue = title,
                    knownKey = null
                )
                val normalizedBody = normalizeNotificationText(
                    rawValue = body,
                    knownKey = null
                )
                showIncomingLocationNotification(
                    context = context,
                    title = normalizedTitle,
                    body = normalizedBody,
                    latitude = coordinates?.latitude,
                    longitude = coordinates?.longitude
                )
                showLocationBanner(context, normalizedTitle, normalizedBody, coordinates)
            }

        }

        Log.d(
            BackendWebSocketTag,
            "Evento websocket recibido en ${destination ?: "unknown"}"
        )
    }

    private fun normalizeNotificationText(
        rawValue: String?,
        knownKey: String?
    ): String? {
        val normalized = rawValue?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (!knownKey.isNullOrBlank() && normalized.equals(knownKey, ignoreCase = true)) {
            return null
        }
        val looksLikeKey = normalized
            .all { it.isUpperCase() || it == '_' || it.isDigit() }
        return normalized.takeUnless { looksLikeKey }
    }

    private fun showMessageBanner(
        context: Context,
        title: String?,
        body: String?
    ) {
        val localizedContext = notificationLocalizedContext(context)
        ScreenNotificationManager.showNotification(
            notificationName = title?.takeIf { it.isNotBlank() }
                ?: localizedContext.getString(R.string.message_notification_received_title),
            text = body?.takeIf { it.isNotBlank() }
                ?: localizedContext.getString(R.string.message_notification_received_body)
        )
    }

    private fun showEmergencyBanner(
        context: Context,
        title: String?,
        body: String?
    ) {
        val localizedContext = notificationLocalizedContext(context)
        ScreenNotificationManager.showNotification(
            notificationName = title?.takeIf { it.isNotBlank() }
                ?: localizedContext.getString(R.string.emergency_notification_received_title),
            text = body?.takeIf { it.isNotBlank() }
                ?: localizedContext.getString(R.string.emergency_notification_received_body)
        )
    }

    private fun showFriendRequestBanner(
        context: Context,
        title: String?,
        body: String?
    ) {
        val localizedContext = notificationLocalizedContext(context)
        ScreenNotificationManager.showNotification(
            notificationName = title?.takeIf { it.isNotBlank() }
                ?: localizedContext.getString(R.string.friend_request_notification_received_title),
            text = body?.takeIf { it.isNotBlank() }
                ?: localizedContext.getString(R.string.friend_request_notification_received_body)
        )
    }

    private fun showLocationBanner(
        context: Context,
        title: String?,
        body: String?,
        coordinates: Coordinates?
    ) {
        val localizedContext = notificationLocalizedContext(context)
        val resolvedBody = body?.takeIf { it.isNotBlank() } ?: if (coordinates != null) {
            localizedContext.getString(
                R.string.location_notification_received_body_with_coords,
                coordinates.latitude,
                coordinates.longitude
            )
        } else {
            localizedContext.getString(R.string.location_notification_received_body)
        }

        ScreenNotificationManager.showNotification(
            notificationName = title?.takeIf { it.isNotBlank() }
                ?: localizedContext.getString(R.string.location_notification_received_title),
            text = resolvedBody
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

            val desiredChannels = desiredChannelSettings.enabledChannels() + alwaysSubscribedChannels
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
            append("host:nattech.fib.upc.edu:40382\n")
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

            val titleKey = sequenceOf(root, dataObject)
                .mapNotNull { objectNode ->
                    objectNode?.optString("titleKey")
                        ?.takeIf { it.isNotBlank() }
                }
                .firstOrNull()
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

            val bodyKey = sequenceOf(root, dataObject)
                .mapNotNull { objectNode ->
                    objectNode?.optString("bodyKey")
                        ?.takeIf { it.isNotBlank() }
                }
                .firstOrNull()
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

        fun extractChatId(): Long? {
            val candidates = listOfNotNull(dataObject, root)

            for (candidate in candidates) {
                if (!candidate.has("chatId")) {
                    continue
                }
                val chatId = candidate.optLong("chatId", -1L)
                if (chatId > 0L) {
                    return chatId
                }
            }

            return null
        }

        fun extractEventType(): String? {
            val candidates = listOfNotNull(dataObject, root)
            val keys = listOf("eventType", "type", "event", "action", "titleKey")
            for (c in candidates) for (k in keys) {
                val v = c.optString(k, "").trim()
                if (v.isNotBlank()) return v
            }
            return null
        }

        fun extractUsername(): String? {
            val candidates = listOfNotNull(dataObject, root)
            val keys = listOf("username", "targetUsername", "userName", "user")
            for (c in candidates) for (k in keys) {
                val v = c.optString(k, "").trim()
                if (v.isNotBlank()) return v
            }
            return null
        }

        fun extractGroupName(): String? {
            val candidates = listOfNotNull(dataObject, root)
            for (c in candidates) {
                val v = c.optString("groupName", "").trim().ifBlank { c.optString("name", "").trim() }
                if (v.isNotBlank()) return v
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
