package com.safesteps.i18n

import java.util.Locale

enum class AppLanguage(
    val languageTag: String,
    val abbreviation: String
) {
    ENGLISH("en", "EN"),
    CATALAN("ca", "CA"),
    SPANISH("es", "ES");

    companion object {
        val default: AppLanguage
            get() {
                //val systemLanguage = Locale.getDefault().language
                //return entries.firstOrNull { it.languageTag.equals(systemLanguage, ignoreCase = true) }
                  //  ?: ENGLISH
                return AppLanguage.CATALAN

            }

        fun fromLanguageTag(languageTag: String?): AppLanguage {
            if (languageTag.isNullOrBlank()) return default
            return entries.firstOrNull { it.languageTag.equals(languageTag, ignoreCase = true) }
                ?: default
        }
    }
}
