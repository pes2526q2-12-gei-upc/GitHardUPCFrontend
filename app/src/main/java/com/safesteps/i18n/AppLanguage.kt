package com.safesteps.i18n

enum class AppLanguage(
    val languageTag: String,
    val abbreviation: String
) {
    ENGLISH("en", "EN"),
    CATALAN("ca", "CA"),
    SPANISH("es", "ES");

    companion object {
        val default = ENGLISH

        fun fromLanguageTag(languageTag: String?): AppLanguage {
            return entries.firstOrNull { it.languageTag.equals(languageTag, ignoreCase = true) }
                ?: default
        }
    }
}
