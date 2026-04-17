package com.safesteps.map

import android.content.Context
import com.safesteps.R
import com.safesteps.i18n.AppLanguage
import com.safesteps.i18n.localizedContext
import java.util.Locale

class MapTextProvider(
    private val context: Context
) {
    fun searchingAddress(language: AppLanguage): String {
        return context.localizedContext(language).getString(R.string.searching_address)
    }

    fun selectedMapLocation(language: AppLanguage): String {
        return context.localizedContext(language).getString(R.string.selected_map_location)
    }

    fun photonLanguage(language: AppLanguage): String {
        return when (language.languageTag.lowercase(Locale.ROOT)) {
            "en" -> "en"
            "de" -> "de"
            "fr" -> "fr"
            else -> "default"
        }
    }
}
