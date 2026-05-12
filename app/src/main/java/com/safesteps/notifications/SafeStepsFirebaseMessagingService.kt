package com.safesteps.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.safesteps.ui.notifications.ScreenNotificationManager

private const val EmergencyNotificationsTag = "EMERGENCY_NOTIFICATIONS"

class SafeStepsFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val tokenPreview = token.takeIf { it.isNotBlank() }
            ?.let { value -> "${value.take(12)}..." }
            ?: "unknown"
        Log.i(EmergencyNotificationsTag, "onNewToken recibido: $tokenPreview")
        syncStoredUserWithNewFcmToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"]
        val body = message.notification?.body ?: message.data["body"]
        Log.i(
            EmergencyNotificationsTag,
            "Push recibida: title=${title ?: "null"} body=${body ?: "null"}"
        )

        showIncomingEmergencyNotification(
            context = applicationContext,
            title = title,
            body = body
        )

        val resolvedTitle = title?.takeIf { it.isNotBlank() }
            ?: getString(com.safesteps.R.string.emergency_notification_received_title)
        val resolvedBody = body?.takeIf { it.isNotBlank() }
            ?: getString(com.safesteps.R.string.emergency_notification_received_body)
        ScreenNotificationManager.showNotification(
            notificationName = resolvedTitle,
            text = resolvedBody
        )
    }
}
