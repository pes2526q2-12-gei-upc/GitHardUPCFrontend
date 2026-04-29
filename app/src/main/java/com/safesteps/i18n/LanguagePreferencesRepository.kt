package com.safesteps.i18n

import android.content.Context
import java.util.Locale

private const val LANGUAGE_PREFERENCES_NAME = "language_preferences"
private const val USER_LANGUAGE_KEY_PREFIX = "user_language_"

class LanguagePreferencesRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        LANGUAGE_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun getLanguageForUser(email: String?): AppLanguage {
        if (email.isNullOrBlank()) {
            return AppLanguage.default
        }

        val storedLanguage = preferences.getString(keyFor(email), null)
        return AppLanguage.fromLanguageTag(storedLanguage)
    }

    fun saveLanguageForUser(email: String, language: AppLanguage) {
        preferences.edit()
            .putString(keyFor(email), language.languageTag)
            .apply()
    }

    private fun keyFor(email: String): String {
        return USER_LANGUAGE_KEY_PREFIX + email.trim().lowercase(Locale.ROOT)
    }
}
