package com.safesteps.map

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val MapEventPreferencesName = "map_event_preferences"
private const val LegacyEventsEnabledPrefKey = "events_enabled"
private const val HideEventsDuringActiveRoutePrefKey = "hide_events_during_active_route"
private const val HideEventsDuringRoutePreviewPrefKey = "hide_events_during_route_preview"

data class MapEventSettings(
    val hideEventsDuringActiveRoute: Boolean = false,
    val hideEventsDuringRoutePreview: Boolean = false
)

object MapEventPreferences {
    private val settingsFlow = MutableStateFlow(MapEventSettings())
    private var preferences: SharedPreferences? = null

    fun settings(context: Context): StateFlow<MapEventSettings> {
        ensureInitialized(context)
        return settingsFlow.asStateFlow()
    }

    fun setHideEventsDuringActiveRoute(
        context: Context,
        enabled: Boolean
    ) {
        ensureInitialized(context)
        val updatedSettings = settingsFlow.value.copy(hideEventsDuringActiveRoute = enabled)
        if (updatedSettings == settingsFlow.value) {
            return
        }

        preferences?.edit()
            ?.putBoolean(HideEventsDuringActiveRoutePrefKey, enabled)
            ?.apply()
        settingsFlow.value = updatedSettings
    }

    fun setHideEventsDuringRoutePreview(
        context: Context,
        enabled: Boolean
    ) {
        ensureInitialized(context)
        val updatedSettings = settingsFlow.value.copy(hideEventsDuringRoutePreview = enabled)
        if (updatedSettings == settingsFlow.value) {
            return
        }

        preferences?.edit()
            ?.putBoolean(HideEventsDuringRoutePreviewPrefKey, enabled)
            ?.apply()
        settingsFlow.value = updatedSettings
    }

    private fun ensureInitialized(context: Context) {
        if (preferences != null) {
            return
        }

        synchronized(this) {
            if (preferences != null) {
                return
            }

            preferences = context.applicationContext.getSharedPreferences(
                MapEventPreferencesName,
                Context.MODE_PRIVATE
            )
            settingsFlow.value = loadSettings(requireNotNull(preferences))
        }
    }

    private fun loadSettings(preferences: SharedPreferences): MapEventSettings {
        val hasNewKeys = preferences.contains(HideEventsDuringActiveRoutePrefKey) ||
            preferences.contains(HideEventsDuringRoutePreviewPrefKey)

        if (hasNewKeys) {
            return MapEventSettings(
                hideEventsDuringActiveRoute = preferences.getBoolean(
                    HideEventsDuringActiveRoutePrefKey,
                    false
                ),
                hideEventsDuringRoutePreview = preferences.getBoolean(
                    HideEventsDuringRoutePreviewPrefKey,
                    false
                )
            )
        }

        val legacyEventsEnabled = preferences.getBoolean(LegacyEventsEnabledPrefKey, true)
        return if (legacyEventsEnabled) {
            MapEventSettings()
        } else {
            MapEventSettings(
                hideEventsDuringActiveRoute = false,
                hideEventsDuringRoutePreview = true
            )
        }
    }
}
