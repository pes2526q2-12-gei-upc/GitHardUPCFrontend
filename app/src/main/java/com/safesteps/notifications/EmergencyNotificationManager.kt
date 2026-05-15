package com.safesteps.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.safesteps.MainActivity
import com.safesteps.R
import com.safesteps.auth.UserInfo
import com.safesteps.data.sincronizarTokenFcmUsuario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val EmergencyChannelId = "emergency_alerts"
private const val ActivityChannelId = "activity_alerts"
private const val EmergencyNotificationId = 2_401
private const val MessageNotificationId = 2_402
private const val FriendRequestNotificationId = 2_403
private const val LocationNotificationId = 2_404
private const val EmergencyNotificationsTag = "EMERGENCY_NOTIFICATIONS"
private const val EmergencyNotificationsPrefs = "emergency_notifications"
private const val CurrentGoogleIdKey = "current_google_id"

private val emergencyNotificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

fun initializeEmergencyMessaging(context: Context) {
    ensureEmergencyNotificationChannel(context)
    ensureActivityNotificationChannel(context)

    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
        if (!task.isSuccessful) {
            Log.w(
                EmergencyNotificationsTag,
                "No se pudo inicializar Firebase Messaging",
                task.exception
            )
            return@addOnCompleteListener
        }

        val tokenPreview = task.result
            ?.takeIf { it.isNotBlank() }
            ?.let { token -> "${token.take(12)}..." }
            ?: "unknown"
        Log.d(EmergencyNotificationsTag, "FCM token listo: $tokenPreview")
    }
}

fun hasNotificationPermission(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
}

fun persistEmergencyNotificationUser(
    context: Context,
    googleId: String?
) {
    context.applicationContext
        .getSharedPreferences(EmergencyNotificationsPrefs, Context.MODE_PRIVATE)
        .edit()
        .putString(CurrentGoogleIdKey, googleId?.takeIf { it.isNotBlank() })
        .apply()
}

suspend fun syncCurrentFcmTokenForUser(
    context: Context,
    googleId: String
) {
    val resolvedGoogleId = googleId.takeIf { it.isNotBlank() } ?: return
    persistEmergencyNotificationUser(context, resolvedGoogleId)

    val token = FirebaseMessaging.getInstance().token.await()
    sincronizarTokenFcmUsuario(resolvedGoogleId, token)
}

fun syncStoredUserWithNewFcmToken(
    context: Context,
    token: String
) {
    val resolvedToken = token.takeIf { it.isNotBlank() } ?: return
    val googleId = context.applicationContext
        .getSharedPreferences(EmergencyNotificationsPrefs, Context.MODE_PRIVATE)
        .getString(CurrentGoogleIdKey, null)
        ?.takeIf { it.isNotBlank() }
        ?: return

    emergencyNotificationScope.launch {
        runCatching {
            sincronizarTokenFcmUsuario(googleId, resolvedToken)
        }.onFailure { error ->
            Log.w(
                EmergencyNotificationsTag,
                "No se pudo sincronizar el token FCM actualizado",
                error
            )
        }
    }
}

fun showTriggeredEmergencyNotification(
    context: Context,
    currentUser: UserInfo?
) {
    val senderName = currentUser?.username?.takeIf(String::isNotBlank)
        ?: context.getString(R.string.app_name)
    val title = context.getString(R.string.emergency_notification_triggered_title)
    val body = context.getString(R.string.emergency_notification_triggered_body, senderName)

    showEmergencyNotification(
        context = context,
        title = title,
        body = body
    )
}

fun showIncomingEmergencyNotification(
    context: Context,
    title: String?,
    body: String?
) {
    showEmergencyNotification(
        context = context,
        title = title?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.emergency_notification_received_title),
        body = body?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.emergency_notification_received_body)
    )
}

fun showIncomingMessageNotification(
    context: Context,
    title: String?,
    body: String?
) {
    showActivityNotification(
        context = context,
        notificationId = MessageNotificationId,
        title = title?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.message_notification_received_title),
        body = body?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.message_notification_received_body),
        category = NotificationCompat.CATEGORY_MESSAGE,
        color = 0xFF1F6F78.toInt()
    )
}

fun showIncomingFriendRequestNotification(
    context: Context,
    title: String?,
    body: String?
) {
    showActivityNotification(
        context = context,
        notificationId = FriendRequestNotificationId,
        title = title?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.friend_request_notification_received_title),
        body = body?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.friend_request_notification_received_body),
        category = NotificationCompat.CATEGORY_SOCIAL,
        color = 0xFF2F855A.toInt()
    )
}

fun showIncomingLocationNotification(
    context: Context,
    title: String?,
    body: String?,
    latitude: Double? = null,
    longitude: Double? = null
) {
    val resolvedBody = body?.takeIf(String::isNotBlank)
        ?: if (latitude != null && longitude != null) {
            context.getString(
                R.string.location_notification_received_body_with_coords,
                latitude,
                longitude
            )
        } else {
            context.getString(R.string.location_notification_received_body)
        }

    showActivityNotification(
        context = context,
        notificationId = LocationNotificationId,
        title = title?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.location_notification_received_title),
        body = resolvedBody,
        category = NotificationCompat.CATEGORY_STATUS,
        color = 0xFF2B6CB0.toInt()
    )
}

private fun ensureEmergencyNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return
    }

    val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
    if (notificationManager.getNotificationChannel(EmergencyChannelId) != null) {
        return
    }

    val channel = NotificationChannel(
        EmergencyChannelId,
        context.getString(R.string.emergency_notification_channel_name),
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = context.getString(R.string.emergency_notification_channel_description)
        enableVibration(true)
    }

    notificationManager.createNotificationChannel(channel)
}

private fun ensureActivityNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return
    }

    val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
    if (notificationManager.getNotificationChannel(ActivityChannelId) != null) {
        return
    }

    val channel = NotificationChannel(
        ActivityChannelId,
        context.getString(R.string.notification_channel_activity_name),
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = context.getString(R.string.notification_channel_activity_description)
        enableVibration(true)
    }

    notificationManager.createNotificationChannel(channel)
}

@SuppressLint("MissingPermission")
private fun showEmergencyNotification(
    context: Context,
    title: String,
    body: String
) {
    ensureEmergencyNotificationChannel(context)
    if (!hasNotificationPermission(context)) {
        Log.d(EmergencyNotificationsTag, "Notificacion omitida: permiso no concedido")
        return
    }

    val contentIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, EmergencyChannelId)
        .setSmallIcon(R.drawable.ic_emergency_notification)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setColor(0xFFB71C3B.toInt())
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(EmergencyNotificationId, notification)
}

@SuppressLint("MissingPermission")
private fun showActivityNotification(
    context: Context,
    notificationId: Int,
    title: String,
    body: String,
    category: String,
    color: Int
) {
    ensureActivityNotificationChannel(context)
    if (!hasNotificationPermission(context)) {
        Log.d(EmergencyNotificationsTag, "Notificacion omitida: permiso no concedido")
        return
    }

    val contentIntent = PendingIntent.getActivity(
        context,
        notificationId,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, ActivityChannelId)
        .setSmallIcon(R.drawable.ic_emergency_notification)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setCategory(category)
        .setColor(color)
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(notificationId, notification)
}
