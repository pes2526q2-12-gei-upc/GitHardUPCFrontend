package com.safesteps.map

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.safesteps.R
import java.util.Locale

class MapTextProvider(
    private val context: Context
) {
    fun searchingAddress(): String = context.getString(R.string.searching_address)

    fun selectedMapLocation(): String = context.getString(R.string.selected_map_location)

    fun photonLanguage(): String {
        val appLanguage = AppCompatDelegate.getApplicationLocales()[0]?.language
            ?: Locale.getDefault().language

        return when (appLanguage.lowercase(Locale.ROOT)) {
            "en" -> "en"
            "de" -> "de"
            "fr" -> "fr"
            else -> "default"
        }
    }
}
