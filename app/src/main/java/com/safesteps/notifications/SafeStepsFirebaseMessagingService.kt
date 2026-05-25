package com.safesteps.notifications

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.safesteps.R

private const val EmergencyNotificationsTag = "EMERGENCY_NOTIFICATIONS"
private const val FcmTag = "FCM_SERVICE"
private const val FriendRequestPendingTitleKey = "FRIEND_REQ_TITLE"
private const val FriendRequestPendingBodyKey = "FRIEND_REQ_BODY"
private const val FriendRequestAcceptedTitleKey = "FRIEND_ACC_TITLE"
private const val FriendRequestAcceptedBodyKey = "FRIEND_ACC_BODY"
private const val FriendRequestRejectedTitleKey = "FRIEND_REJ_TITLE"
private const val FriendRequestRejectedBodyKey = "FRIEND_REJ_BODY"

class SafeStepsFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        syncStoredUserWithNewFcmToken(applicationContext, token)

        val tokenPreview = token.takeIf { it.isNotBlank() }
            ?.let { value -> "${value.take(12)}..." }
            ?: "unknown"
        Log.d(EmergencyNotificationsTag, "Nuevo FCM token: $tokenPreview")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = message.notification?.title ?: message.data["title"]
        val body = message.notification?.body ?: message.data["body"]
        val senderName = resolveFriendRequestSenderName(data)
        val status = data["status"] ?: data["friendshipStatus"] ?: data["requestStatus"]

        val senderUsername = (message.data["sender_username"] ?: message.data["senderUsername"])?.trim().orEmpty()
        val messageContent = (message.data["message_content"] ?: message.data["messageContent"])?.trim().orEmpty()
        val chatId = (message.data["chatId"] ?: message.data["chat_id"])?.toLongOrNull()
        val senderGoogleId = (message.data["senderGoogleId"] ?: message.data["sender_google_id"])?.trim().orEmpty()
        val chatName = sequenceOf("chatName", "chat_name", "groupName", "group_name")
            .mapNotNull { k -> message.data[k]?.trim()?.takeIf { it.isNotBlank() } }
            .firstOrNull().orEmpty()
        val isGroup = (message.data["chatType"] ?: message.data["chat_type"]).equals("GROUP", true) ||
                (message.data["isGroup"] ?: message.data["is_group"]).equals("true", true) ||
                message.data.containsKey("groupName") || message.data.containsKey("group_name")
        val avatarUrl = sequenceOf("senderPhotoUrl", "sender_photo_url", "avatarUrl", "photoUrl", "picture")
            .mapNotNull { k -> message.data[k]?.trim()?.takeIf { it.isNotBlank() } }
            .firstOrNull()

        if (isFriendRequestNotification(title, body, status)) {
            showIncomingFriendRequestNotification(
                context = applicationContext,
                title = title,
                body = body,
                senderName = senderName,
                status = status
            )
            return
        }

        Log.d(FcmTag, "==== FCM recibido (background/cerrada) ====")
        Log.d(FcmTag, "title='$title' body='$body'")
        message.data.forEach { (k, v) -> Log.d(FcmTag, "  data['$k'] = '$v'") }

        val normalizedType = resolveNotificationType(message)
        Log.d(FcmTag, "-> Routing as $normalizedType")

        val localizedContext = notificationLocalizedContext(applicationContext)
        val resolvedTitle = resolveLocalizedTitle(
            context = localizedContext,
            type = normalizedType,
            data = message.data
        )
        val resolvedBody = resolveLocalizedBody(
            context = localizedContext,
            type = normalizedType,
            data = message.data
        )

        when {
            normalizedType.contains("MESSAGE") -> showIncomingMessageNotification(
                context = applicationContext,
                chatId = chatId,
                chatName = chatName.ifBlank { senderUsername },
                senderName = senderUsername,
                messageContent = messageContent.ifBlank { resolvedBody.orEmpty() },
                isGroup = isGroup,
                avatarUrl = avatarUrl,
                senderGoogleId = senderGoogleId.takeIf { it.isNotBlank() }
            )
            normalizedType.contains("FRIEND") -> showIncomingFriendRequestNotification(
                context = applicationContext,
                title = resolvedTitle,
                body = resolvedBody
            )
            normalizedType.contains("LOCATION") -> showIncomingLocationNotification(
                context = applicationContext,
                title = resolvedTitle,
                body = resolvedBody,
                latitude = message.data["lat"]?.toDoubleOrNull() ?: message.data["latitude"]?.toDoubleOrNull(),
                longitude = message.data["lon"]?.toDoubleOrNull()
                    ?: message.data["lng"]?.toDoubleOrNull()
                    ?: message.data["longitude"]?.toDoubleOrNull()
            )
            else -> showIncomingEmergencyNotification(
                context = applicationContext,
                title = resolvedTitle,
                body = resolvedBody
            )
        }
    }

    private fun resolveLocalizedTitle(
        context: Context,
        type: String,
        data: Map<String, String>
    ): String {
        val titleKey = data["titleKey"]?.trim().orEmpty()
        if (titleKey.isNotBlank()) {
            resolveTitleFromKey(context, titleKey)?.let { return it }
        }

        return when {
            type.contains("MESSAGE") -> context.getString(R.string.message_notification_received_title)
            type.contains("FRIEND") -> context.getString(R.string.friend_request_notification_received_title)
            type.contains("LOCATION") -> context.getString(R.string.location_notification_received_title)
            else -> context.getString(R.string.emergency_notification_received_title)
        }
    }

    private fun resolveLocalizedBody(
        context: Context,
        type: String,
        data: Map<String, String>
    ): String? {
        val bodyKey = data["bodyKey"]?.trim().orEmpty()
        if (bodyKey.isNotBlank()) {
            resolveBodyFromKey(context, bodyKey)?.let { return it }
        }

        return when {
            type.contains("MESSAGE") -> context.getString(R.string.message_notification_received_body)
            type.contains("FRIEND") -> context.getString(R.string.friend_request_notification_received_body)
            type.contains("LOCATION") -> null
            else -> context.getString(R.string.emergency_notification_received_body)
        }
    }

    private fun resolveTitleFromKey(
        context: Context,
        key: String
    ): String? {
        val normalizedKey = key.uppercase()
        return when {
            normalizedKey.contains("MESSAGE") -> context.getString(R.string.message_notification_received_title)
            normalizedKey.contains("FRIEND") -> context.getString(R.string.friend_request_notification_received_title)
            normalizedKey.contains("LOCATION") -> context.getString(R.string.location_notification_received_title)
            normalizedKey.contains("EMERGENCY") || normalizedKey.contains("SOS") ->
                context.getString(R.string.emergency_notification_received_title)

            else -> null
        }
    }

    private fun resolveBodyFromKey(
        context: Context,
        key: String
    ): String? {
        val normalizedKey = key.uppercase()
        return when {
            normalizedKey.contains("MESSAGE") -> context.getString(R.string.message_notification_received_body)
            normalizedKey.contains("FRIEND") -> context.getString(R.string.friend_request_notification_received_body)
            normalizedKey.contains("LOCATION") -> null
            normalizedKey.contains("EMERGENCY") || normalizedKey.contains("SOS") ->
                context.getString(R.string.emergency_notification_received_body)

            else -> null
        }
    }

    private fun resolveNotificationType(message: RemoteMessage): String {
        val data = message.data
        val title = (message.notification?.title ?: data["title"] ?: "").lowercase()
        val body = (message.notification?.body ?: data["body"] ?: "").lowercase()

        val explicitType = sequenceOf("type", "notificationType", "eventType", "channel")
            .mapNotNull { key -> data[key]?.trim()?.takeIf { it.isNotBlank() } }
            .firstOrNull()
            ?.uppercase()
        if (explicitType != null) {
            return explicitType
        }

        val titleKey = data["titleKey"]?.uppercase().orEmpty()
        val bodyKey = data["bodyKey"]?.uppercase().orEmpty()

        if (data.containsKey("emergencyId") || data.containsKey("sosId") ||
            data.keys.any { it.lowercase().contains("emergency") }
        ) {
            return "EMERGENCY"
        }
        if (titleKey.contains("EMERGENCY") || bodyKey.contains("EMERGENCY")) {
            return "EMERGENCY"
        }

        if (data.containsKey("chatId") || data.containsKey("chat_id") ||
            data.containsKey("senderUsername") || data.containsKey("sender_username") ||
            data.containsKey("senderGoogleId") || data.containsKey("messageContent") || data.containsKey("message_content")
        ) {
            return "MESSAGE"
        }
        if (titleKey.contains("MESSAGE") || bodyKey.contains("MESSAGE")) {
            return "MESSAGE"
        }
        if (titleKey.contains("CHAT") || bodyKey.contains("CHAT")) {
            return "MESSAGE"
        }

        if (titleKey.contains("FRIEND") || bodyKey.contains("FRIEND")) {
            return "FRIEND_REQUEST"
        }
        if (data.keys.any { it.lowercase().contains("friend") }) {
            return "FRIEND_REQUEST"
        }

        if (data.containsKey("lat") || data.containsKey("latitude")) {
            return "LOCATION"
        }

        val emergencyKeywords = listOf(
            "emergencia", "emergency",
            "sos", "alerta de emergencia", "emergency alert"
        )
        if (emergencyKeywords.any { it in title || it in body }) {
            return "EMERGENCY"
        }

        val messageKeywords = listOf(
            "nuevo mensaje", "nou missatge", "new message",
            "mensaje de", "missatge de", "message from",
            "te ha enviado", "t'ha enviat", "sent you"
        )
        if (messageKeywords.any { it in title || it in body }) {
            return "MESSAGE"
        }

        val friendKeywords = listOf(
            "solicitud de amistad", "solicitud d'amistat", "friend request",
            "nueva amistad", "nova amistat", "new friend"
        )
        if (friendKeywords.any { it in title || it in body }) {
            return "FRIEND_REQUEST"
        }

        Log.w(FcmTag, "FCM sin tipo claro -> fallback a MESSAGE")
        return "MESSAGE"
    }
}

private fun resolveFriendRequestSenderName(data: Map<String, String>): String? {
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

    return candidateKeys
        .asSequence()
        .mapNotNull { key -> data[key]?.trim()?.takeIf { it.isNotBlank() } }
        .firstOrNull()
}

private fun isFriendRequestNotification(
    title: String?,
    body: String?,
    status: String?
): Boolean {
    val candidates = listOf(title, body, status)
    return candidates.any { it.equals("PENDING", ignoreCase = true) } ||
        candidates.any { it.equals("ACCEPTED", ignoreCase = true) } ||
        candidates.any { it.equals("REJECTED", ignoreCase = true) } ||
        candidates.any { it.equals("DECLINED", ignoreCase = true) } ||
        candidates.any { it.equals("DENIED", ignoreCase = true) } ||
        candidates.any { it.equals(FriendRequestPendingTitleKey, ignoreCase = true) } ||
        candidates.any { it.equals(FriendRequestPendingBodyKey, ignoreCase = true) } ||
        candidates.any { it.equals(FriendRequestAcceptedTitleKey, ignoreCase = true) } ||
        candidates.any { it.equals(FriendRequestAcceptedBodyKey, ignoreCase = true) } ||
        candidates.any { it.equals(FriendRequestRejectedTitleKey, ignoreCase = true) } ||
        candidates.any { it.equals(FriendRequestRejectedBodyKey, ignoreCase = true) }
}
