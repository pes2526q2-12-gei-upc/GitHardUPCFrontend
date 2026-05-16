package com.safesteps.notifications

import android.content.Context
import com.safesteps.i18n.AppLanguage
import com.safesteps.i18n.localizedContext

private const val NotificationPreferencesName = "emergency_notifications"
private const val NotificationLanguageTagKey = "current_language_tag"

fun persistNotificationLanguageTag(
    context: Context,
    languageTag: String?
) {
    context.applicationContext
        .getSharedPreferences(NotificationPreferencesName, Context.MODE_PRIVATE)
        .edit()
        .putString(
            NotificationLanguageTagKey,
            languageTag?.takeIf { it.isNotBlank() }
        )
        .apply()
}

fun notificationLocalizedContext(context: Context): Context {
    val languageTag = context.applicationContext
        .getSharedPreferences(NotificationPreferencesName, Context.MODE_PRIVATE)
        .getString(NotificationLanguageTagKey, null)
    return context.localizedContext(AppLanguage.fromLanguageTag(languageTag))
}
