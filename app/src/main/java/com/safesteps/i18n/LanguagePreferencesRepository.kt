package com.safesteps.i18n

import android.content.Context
import java.util.Locale

private const val LANGUAGE_PREFERENCES_NAME = "language_preferences"
private const val USER_LANGUAGE_KEY_PREFIX = "user_language_"
private const val GLOBAL_LANGUAGE_KEY = "global_app_language"

class LanguagePreferencesRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        LANGUAGE_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun getLanguageForUser(email: String?): AppLanguage {
        val storedLanguage = if (!email.isNullOrBlank()) {
            preferences.getString(keyFor(email), null)
        } else {
            null
        }

        // Si no hay idioma para el usuario, o el usuario es null, usamos el global
        return if (storedLanguage != null) {
            AppLanguage.fromLanguageTag(storedLanguage)
        } else {
            val globalLanguage = preferences.getString(GLOBAL_LANGUAGE_KEY, null)
            AppLanguage.fromLanguageTag(globalLanguage)
        }
    }

    fun saveLanguageForUser(email: String?, language: AppLanguage) {
        val editor = preferences.edit()
        
        // Siempre guardamos como global para persistencia entre sesiones/logouts
        editor.putString(GLOBAL_LANGUAGE_KEY, language.languageTag)
        
        if (!email.isNullOrBlank()) {
            editor.putString(keyFor(email), language.languageTag)
        }
        
        editor.apply()
    }

    private fun keyFor(email: String): String {
        return USER_LANGUAGE_KEY_PREFIX + email.trim().lowercase(Locale.ROOT)
    }
}
