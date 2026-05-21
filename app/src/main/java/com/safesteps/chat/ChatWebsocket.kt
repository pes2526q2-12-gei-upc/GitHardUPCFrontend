package com.safesteps.chat

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class ChatRealtimeEvent {
    abstract val chatId: Long?

    data class NewMessage(override val chatId: Long?) : ChatRealtimeEvent()
    data class ParticipantLeft(override val chatId: Long?, val username: String?) : ChatRealtimeEvent()
    data class ParticipantJoined(override val chatId: Long?, val username: String?) : ChatRealtimeEvent()
    data class ParticipantKicked(override val chatId: Long?, val username: String?) : ChatRealtimeEvent()
    data class AdminPromoted(override val chatId: Long?, val username: String?) : ChatRealtimeEvent()
    data class AdminRevoked(override val chatId: Long?, val username: String?) : ChatRealtimeEvent()
    data class GroupCreated(override val chatId: Long?, val groupName: String?) : ChatRealtimeEvent()
}

object ChatEventBus {
    private val _events = MutableSharedFlow<ChatRealtimeEvent>(replay = 0, extraBufferCapacity = 16)
    val events: SharedFlow<ChatRealtimeEvent> = _events.asSharedFlow()

    val newMessageEvent: SharedFlow<ChatRealtimeEvent> = events

    fun onNewMessage(chatId: Long? = null) { _events.tryEmit(ChatRealtimeEvent.NewMessage(chatId)) }
    fun onParticipantLeft(chatId: Long?, username: String? = null) { _events.tryEmit(ChatRealtimeEvent.ParticipantLeft(chatId, username)) }
    fun onParticipantJoined(chatId: Long?, username: String? = null) { _events.tryEmit(ChatRealtimeEvent.ParticipantJoined(chatId, username)) }
    fun onParticipantKicked(chatId: Long?, username: String? = null) { _events.tryEmit(ChatRealtimeEvent.ParticipantKicked(chatId, username)) }
    fun onAdminPromoted(chatId: Long?, username: String? = null) { _events.tryEmit(ChatRealtimeEvent.AdminPromoted(chatId, username)) }
    fun onAdminRevoked(chatId: Long?, username: String? = null) { _events.tryEmit(ChatRealtimeEvent.AdminRevoked(chatId, username)) }
    fun onGroupCreated(chatId: Long?, groupName: String? = null) { _events.tryEmit(ChatRealtimeEvent.GroupCreated(chatId, groupName)) }
}