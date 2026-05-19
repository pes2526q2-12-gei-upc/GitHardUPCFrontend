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
private const val EmergencyStartedTitleKey = "EMERGENCY_TITLE"
private const val EmergencyStartedBodyKey = "EMERGENCY_BODY"
private const val EmergencyEndedTitleKey = "EMERGENCY_END_TITLE"
private const val EmergencyEndedBodyKey = "EMERGENCY_END_BODY"
private const val FriendRequestPendingTitleKey = "FRIEND_REQ_TITLE"
private const val FriendRequestPendingBodyKey = "FRIEND_REQ_BODY"
private const val FriendRequestAcceptedTitleKey = "FRIEND_ACC_TITLE"
private const val FriendRequestAcceptedBodyKey = "FRIEND_ACC_BODY"
private const val FriendRequestRejectedTitleKey = "FRIEND_REJ_TITLE"
private const val FriendRequestRejectedBodyKey = "FRIEND_REJ_BODY"

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
    body: String?,
    senderName: String? = null,
    titleKey: String? = null,
    bodyKey: String? = null
) {
    val resolvedSenderName = senderName?.trim()?.takeIf { it.isNotBlank() }
    val emergencyState = resolveIncomingEmergencyState(
        title = title,
        body = body,
        titleKey = titleKey,
        bodyKey = bodyKey
    )

    val finalTitle = title?.takeIf(String::isNotBlank)
        ?.takeUnless(::isEmergencyTemplateKey)
        ?: context.getString(R.string.emergency_notification_received_title)

    val finalBody = resolveIncomingEmergencyBody(
        context = context,
        emergencyState = emergencyState,
        senderName = resolvedSenderName
    ).takeIf { it.isNotBlank() }
        ?: body?.takeIf(String::isNotBlank)
        ?: context.getString(R.string.emergency_notification_received_body)

    showEmergencyNotification(
        context = context,
        title = finalTitle,
        body = finalBody
    )
}

private enum class IncomingEmergencyState {
    ACTIVATED,
    DEACTIVATED,
    UNKNOWN
}

private fun resolveIncomingEmergencyState(
    title: String?,
    body: String?,
    titleKey: String?,
    bodyKey: String?
): IncomingEmergencyState {
    val candidates = listOf(title, body, titleKey, bodyKey)
    return when {
        candidates.any { it.equals(EmergencyEndedTitleKey, ignoreCase = true) } ||
            candidates.any { it.equals(EmergencyEndedBodyKey, ignoreCase = true) } -> {
            IncomingEmergencyState.DEACTIVATED
        }

        candidates.any { it.equals(EmergencyStartedTitleKey, ignoreCase = true) } ||
            candidates.any { it.equals(EmergencyStartedBodyKey, ignoreCase = true) } -> {
            IncomingEmergencyState.ACTIVATED
        }

        else -> IncomingEmergencyState.UNKNOWN
    }
}

private fun resolveIncomingEmergencyBody(
    context: Context,
    emergencyState: IncomingEmergencyState,
    senderName: String?
): String {
    return when (emergencyState) {
        IncomingEmergencyState.ACTIVATED -> senderName?.let {
            context.getString(R.string.emergency_notification_contact_activated_body, it)
        } ?: context.getString(R.string.emergency_notification_contact_activated_body_generic)

        IncomingEmergencyState.DEACTIVATED -> senderName?.let {
            context.getString(R.string.emergency_notification_contact_deactivated_body, it)
        } ?: context.getString(R.string.emergency_notification_contact_deactivated_body_generic)

        IncomingEmergencyState.UNKNOWN -> senderName?.let {
            context.getString(R.string.emergency_notification_contact_activated_body, it)
        } ?: context.getString(R.string.emergency_notification_received_body)
    }
}

private fun isEmergencyTemplateKey(value: String): Boolean {
    return value.equals(EmergencyStartedTitleKey, ignoreCase = true) ||
        value.equals(EmergencyStartedBodyKey, ignoreCase = true) ||
        value.equals(EmergencyEndedTitleKey, ignoreCase = true) ||
        value.equals(EmergencyEndedBodyKey, ignoreCase = true)
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
    body: String?,
    senderName: String? = null,
    status: String? = null
) {
    val resolvedSenderName = senderName?.trim()?.takeIf { it.isNotBlank() }
    val friendRequestState = resolveIncomingFriendRequestState(
        status = status,
        title = title,
        body = body
    )
    val resolvedTitle = title?.takeIf(String::isNotBlank)
        ?.takeUnless(::isFriendRequestTemplateKey)
        ?: when (friendRequestState) {
            IncomingFriendRequestState.ACCEPTED,
            IncomingFriendRequestState.REJECTED -> {
                context.getString(R.string.friend_request_notification_activity_title)
            }

            IncomingFriendRequestState.PENDING,
            IncomingFriendRequestState.UNKNOWN -> {
                context.getString(R.string.friend_request_notification_received_title)
            }
        }
    val resolvedBody = body?.takeIf(String::isNotBlank)
        ?.takeUnless(::isFriendRequestTemplateKey)
        ?: resolveIncomingFriendRequestBody(
            context = context,
            state = friendRequestState,
            senderName = resolvedSenderName
        )

    showActivityNotification(
        context = context,
        notificationId = FriendRequestNotificationId,
        title = resolvedTitle,
        body = resolvedBody,
        category = NotificationCompat.CATEGORY_SOCIAL,
        color = 0xFF2F855A.toInt()
    )
}

private enum class IncomingFriendRequestState {
    PENDING,
    ACCEPTED,
    REJECTED,
    UNKNOWN
}

private fun resolveIncomingFriendRequestState(
    status: String?,
    title: String?,
    body: String?
): IncomingFriendRequestState {
    val candidates = listOf(status, title, body)
    return when {
        candidates.any { it.equals("PENDING", ignoreCase = true) } ||
            candidates.any { it.equals(FriendRequestPendingTitleKey, ignoreCase = true) } ||
            candidates.any { it.equals(FriendRequestPendingBodyKey, ignoreCase = true) } -> {
            IncomingFriendRequestState.PENDING
        }

        candidates.any { it.equals("ACCEPTED", ignoreCase = true) } ||
            candidates.any { it.equals(FriendRequestAcceptedTitleKey, ignoreCase = true) } ||
            candidates.any { it.equals(FriendRequestAcceptedBodyKey, ignoreCase = true) } -> {
            IncomingFriendRequestState.ACCEPTED
        }

        candidates.any { it.equals("REJECTED", ignoreCase = true) } ||
            candidates.any { it.equals("DECLINED", ignoreCase = true) } ||
            candidates.any { it.equals("DENIED", ignoreCase = true) } ||
            candidates.any { it.equals(FriendRequestRejectedTitleKey, ignoreCase = true) } ||
            candidates.any { it.equals(FriendRequestRejectedBodyKey, ignoreCase = true) } -> {
            IncomingFriendRequestState.REJECTED
        }

        else -> IncomingFriendRequestState.UNKNOWN
    }
}

private fun resolveIncomingFriendRequestBody(
    context: Context,
    state: IncomingFriendRequestState,
    senderName: String?
): String {
    return when (state) {
        IncomingFriendRequestState.PENDING -> senderName?.let {
            context.getString(R.string.friend_request_notification_received_body_with_name, it)
        } ?: context.getString(R.string.friend_request_notification_received_body)

        IncomingFriendRequestState.ACCEPTED -> senderName?.let {
            context.getString(R.string.friend_request_notification_accepted_body_with_name, it)
        } ?: context.getString(R.string.friend_request_notification_accepted_body)

        IncomingFriendRequestState.REJECTED -> senderName?.let {
            context.getString(R.string.friend_request_notification_rejected_body_with_name, it)
        } ?: context.getString(R.string.friend_request_notification_rejected_body)

        IncomingFriendRequestState.UNKNOWN -> senderName?.let {
            context.getString(R.string.friend_request_notification_received_body_with_name, it)
        } ?: context.getString(R.string.friend_request_notification_received_body)
    }
}

private fun isFriendRequestTemplateKey(value: String): Boolean {
    return value.equals(FriendRequestPendingTitleKey, ignoreCase = true) ||
        value.equals(FriendRequestPendingBodyKey, ignoreCase = true) ||
        value.equals(FriendRequestAcceptedTitleKey, ignoreCase = true) ||
        value.equals(FriendRequestAcceptedBodyKey, ignoreCase = true) ||
        value.equals(FriendRequestRejectedTitleKey, ignoreCase = true) ||
        value.equals(FriendRequestRejectedBodyKey, ignoreCase = true)
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
