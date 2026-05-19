package com.safesteps.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

private const val EmergencyNotificationsTag = "EMERGENCY_NOTIFICATIONS"
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

        showIncomingEmergencyNotification(
            context = applicationContext,
            title = title,
            body = body
        )
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
