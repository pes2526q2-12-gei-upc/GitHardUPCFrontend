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

    fun routeRecalculated(language: AppLanguage, distanceText: String): String {
        return context.localizedContext(language).getString(
            R.string.navigation_route_recalculated_notice,
            distanceText
        )
    }

        fun routeCompletedPoints(language: AppLanguage, points: Long): String {
        return context.localizedContext(language).getString(
            R.string.route_completed_points,
            points
        )
    }

    fun notificationTitleMap(language: AppLanguage): String {
        return context.localizedContext(language).getString(R.string.notification_title_map)
    }
    fun photonLanguage(language: AppLanguage): String {
        return when (language.languageTag.substring(0, 2).lowercase(Locale.ROOT)) {
            "de" -> "de"
            "fr" -> "fr"
            "it" -> "it"
            "ca" -> "en"
            "es" -> "en"
            else -> "en"
        }
    }
}
