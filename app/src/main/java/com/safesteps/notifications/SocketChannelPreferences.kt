package com.safesteps.notifications

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val SocketChannelPreferencesName = "socket_channel_preferences"

enum class SocketChannelPreference(
    val prefKey: String,
    val destination: String,
    val subscriptionId: String
) {
    MESSAGES(
        prefKey = "socket_messages_enabled",
        destination = "/user/queue/messages",
        subscriptionId = "messages-subscription"
    ),
    EMERGENCY(
        prefKey = "socket_emergency_enabled",
        destination = "/user/queue/emergency",
        subscriptionId = "emergency-subscription"
    ),
    FRIEND_REQUESTS(
        prefKey = "socket_friend_requests_enabled",
        destination = "/user/queue/requests",
        subscriptionId = "friend-requests-subscription"
    ),
    LOCATION(
        prefKey = "socket_location_enabled",
        destination = "/user/queue/location",
        subscriptionId = "location-subscription"
    );

    companion object {
        fun fromDestination(destination: String?): SocketChannelPreference? {
            if (destination.isNullOrBlank()) return null
            values().firstOrNull { it.destination == destination }?.let { return it }

            val normalized = destination.lowercase()
            return when {
                normalized.contains("/messages") || normalized.endsWith("messages") -> MESSAGES
                normalized.contains("/emergency") || normalized.endsWith("emergency") -> EMERGENCY
                normalized.contains("/requests") || normalized.contains("/friend") -> FRIEND_REQUESTS
                normalized.contains("/location") -> LOCATION
                else -> null
            }
        }

        fun fromSubscriptionId(subscriptionId: String?): SocketChannelPreference? {
            if (subscriptionId.isNullOrBlank()) return null
            return values().firstOrNull { it.subscriptionId.equals(subscriptionId, ignoreCase = true) }
        }
    }
}

data class SocketChannelSettings(
    val messagesEnabled: Boolean = true,
    val emergencyEnabled: Boolean = true,
    val friendRequestsEnabled: Boolean = true,
    val locationEnabled: Boolean = true
) {
    fun isEnabled(channel: SocketChannelPreference): Boolean {
        return when (channel) {
            SocketChannelPreference.MESSAGES -> messagesEnabled
            SocketChannelPreference.EMERGENCY -> emergencyEnabled
            SocketChannelPreference.FRIEND_REQUESTS -> friendRequestsEnabled
            SocketChannelPreference.LOCATION -> locationEnabled
        }
    }

    fun updated(
        channel: SocketChannelPreference,
        enabled: Boolean
    ): SocketChannelSettings {
        return when (channel) {
            SocketChannelPreference.MESSAGES -> copy(messagesEnabled = enabled)
            SocketChannelPreference.EMERGENCY -> copy(emergencyEnabled = enabled)
            SocketChannelPreference.FRIEND_REQUESTS -> copy(friendRequestsEnabled = enabled)
            SocketChannelPreference.LOCATION -> copy(locationEnabled = enabled)
        }
    }

    fun enabledChannels(): Set<SocketChannelPreference> {
        return SocketChannelPreference.values()
            .filterTo(mutableSetOf()) { channel -> isEnabled(channel) }
    }
}

object SocketChannelPreferences {
    private val settingsFlow = MutableStateFlow(SocketChannelSettings())
    private var preferences: SharedPreferences? = null

    fun settings(context: Context): StateFlow<SocketChannelSettings> {
        ensureInitialized(context)
        return settingsFlow.asStateFlow()
    }

    fun setChannelEnabled(
        context: Context,
        channel: SocketChannelPreference,
        enabled: Boolean
    ) {
        ensureInitialized(context)
        val currentSettings = settingsFlow.value
        val updatedSettings = currentSettings.updated(channel, enabled)
        if (updatedSettings == currentSettings) {
            return
        }

        preferences?.edit()
            ?.putBoolean(channel.prefKey, enabled)
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
                SocketChannelPreferencesName,
                Context.MODE_PRIVATE
            )
            settingsFlow.value = loadSettings(requireNotNull(preferences))
        }
    }

    private fun loadSettings(preferences: SharedPreferences): SocketChannelSettings {
        return SocketChannelSettings(
            messagesEnabled = preferences.getBoolean(
                SocketChannelPreference.MESSAGES.prefKey,
                true
            ),
            emergencyEnabled = preferences.getBoolean(
                SocketChannelPreference.EMERGENCY.prefKey,
                true
            ),
            friendRequestsEnabled = preferences.getBoolean(
                SocketChannelPreference.FRIEND_REQUESTS.prefKey,
                true
            ),
            locationEnabled = preferences.getBoolean(
                SocketChannelPreference.LOCATION.prefKey,
                true
            )
        )
    }
}
