package com.safesteps.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

private const val EmergencyNotificationsTag = "EMERGENCY_NOTIFICATIONS"

class SafeStepsFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        syncStoredUserWithNewFcmToken(applicationContext, token)

        val tokenPreview = token.takeIf { it.isNotBlank() }
            ?.let { value -> "${value.take(12)}..." }
            ?: "unknown"
        Log.d(EmergencyNotificationsTag, "Nuevo FCM token: $tokenPreview")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"]
        val body = message.notification?.body ?: message.data["body"]

        showIncomingEmergencyNotification(
            context = applicationContext,
            title = title,
            body = body
        )
    }
}
