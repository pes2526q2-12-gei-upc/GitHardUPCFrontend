package com.safesteps.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safesteps.R
import com.safesteps.data.ChatDto
import com.safesteps.data.MessageDto
import com.safesteps.data.afegirParticipant
import com.safesteps.data.cargarAmigosUsuario
import com.safesteps.data.cargarPerfilDeUsuario
import com.safesteps.data.enviarMissatge
import com.safesteps.data.expulsarParticipant
import com.safesteps.data.marcarMissatgeLlegit
import com.safesteps.data.obtenirDataActual
import com.safesteps.data.obtenirMissatges
import com.safesteps.data.obtenirXatsUsuari
import com.safesteps.data.promocionarAdmin
import com.safesteps.data.revocarAdmin
import com.safesteps.data.sortirDelXat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class MessageUiState(
    val id: Long,
    val content: String,
    val senderGoogleId: String,
    val senderUsername: String,
    val createdAt: String,
    val isFromMe: Boolean,
    val isOptimistic: Boolean = false,
    val isRead: Boolean = false,
    val localId: String? = null
)

sealed class ConversationEvent {
    data class Success(val msgResId: Int, val args: List<Any> = emptyList()) : ConversationEvent()
    data class Error(val msgResId: Int, val args: List<Any> = emptyList()) : ConversationEvent()
    data class Info(val msgResId: Int, val args: List<Any> = emptyList()) : ConversationEvent()
    data class NotAdmin(val msgResId: Int, val args: List<Any> = emptyList()) : ConversationEvent()
}

data class ConversationUiState(
    val isLoadingHistory: Boolean = false,
    val isReady: Boolean = false,
    val loadFailed: Boolean = false,
    val messages: List<MessageUiState> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
    val sendFailed: Boolean = false,
    val otherParticipantName: String = "",
    val isGroup: Boolean = false,
    val participantNames: List<String> = emptyList(),
    val participantGoogleIds: List<String> = emptyList(),
    val otherParticipantGoogleId: String? = null,

    val adminGoogleIds: Set<String> = emptySet(),
    val creatorGoogleId: String? = null,
    val currentUserIsAdmin: Boolean = false,
    val currentUserIsCreator: Boolean = false,
    val isProcessingAdmin: Boolean = false,

    val addableFriends: List<FriendForChat> = emptyList(),
    val isLoadingAddableFriends: Boolean = false
)

private const val POLLING_INTERVAL_MS = 3_000L

class ChatConversationViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private val _avatars = MutableStateFlow<Map<String, String>>(emptyMap())
    val avatars: StateFlow<Map<String, String>> = _avatars.asStateFlow()

    private val _events = MutableSharedFlow<ConversationEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ConversationEvent> = _events.asSharedFlow()


    private var chatId: Long = -1
    private var myGoogleId: String = ""
    private var myUsername: String = ""
    private var pollingJob: Job? = null
    private var realtimeEventsJob: Job? = null
    private var historyJob: Job? = null
    private var detailsJob: Job? = null
    private val alreadyMarkedIds = mutableSetOf<Long>()
    private var historyLoaded = false
    private var detailsLoaded = false

    fun init(chatId: Long, myGoogleId: String, myUsername: String, otherParticipantName: String) {
        if (this.chatId == chatId && this.myGoogleId == myGoogleId) {
            return
        }

        historyJob?.cancel()
        detailsJob?.cancel()

        this.chatId = chatId
        this.myGoogleId = myGoogleId
        this.myUsername = myUsername
        alreadyMarkedIds.clear()
        historyLoaded = false
        detailsLoaded = false

        _uiState.value = ConversationUiState(
            otherParticipantName = otherParticipantName,
            isLoadingHistory = true,
            isReady = false,
            messages = emptyList()
        )

        loadHistory()
        loadChatDetails()
    }

    fun onScreenVisible() {
        ChatEventBus.ActiveChatTracker.activeChatId = chatId
        UnreadMessagesStore.clear(chatId)
        startPolling()
    }
    fun onScreenHidden() {
        if (ChatEventBus.ActiveChatTracker.activeChatId == chatId) ChatEventBus.ActiveChatTracker.activeChatId = null
        stopPolling()
    }

    fun onInputChanged(text: String) = _uiState.update { it.copy(inputText = text, sendFailed = false) }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isSending || chatId == -1L || myGoogleId.isBlank()) return

        val localId = UUID.randomUUID().toString()
        val optimistic =
            MessageUiState(
                id = -1L,
                content = text,
                senderGoogleId = myGoogleId,
                senderUsername = myUsername,
                createdAt = formatDate(obtenirDataActual()),
                isFromMe = true,
                isOptimistic = true,
                localId = localId
            )

        _uiState.update { it.copy(messages = it.messages + optimistic, inputText = "", isSending = true, sendFailed = false) }
        viewModelScope.launch {
            try {
                val sent = withContext(ioDispatcher) { enviarMissatge(chatId, myGoogleId, text) }
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.localId == localId) sent.toUiState() else msg
                        },
                        isSending = false
                    )
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                Log.e("CHAT_CONV", "Error enviant: ${e.message}")
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.filterNot { it.localId == localId },
                        isSending = false, sendFailed = true, inputText = text
                    )
                }
                _events.emit(ConversationEvent.Error(R.string.error_sending_message))
            }
        }
    }

    fun carregarAvatarSiCal(googleId: String) {
        if (googleId.isBlank()) return
        val cached = _avatars.value[googleId]
        if (!cached.isNullOrBlank()) return
        viewModelScope.launch {
            try {
                val profile = withContext(ioDispatcher) { cargarPerfilDeUsuario(googleId) }
                val url = profile?.pictureUrl?.trim()
                if (!url.isNullOrBlank()) {
                    Log.d("AVATAR", "cargarPerfil($googleId) → $url")
                    _avatars.update { it + (googleId to url) }
                    return@launch
                }
                val amics = withContext(ioDispatcher) { cargarAmigosUsuario(myGoogleId) }
                val urlAmics = amics.firstOrNull { it.googleId == googleId }?.photoUrl?.trim()
                Log.d("AVATAR", "amics($googleId) → $urlAmics")
                if (!urlAmics.isNullOrBlank()) {
                    _avatars.update { it + (googleId to urlAmics) }
                } else {
                    _avatars.update { it + (googleId to "") }
                }
            } catch (e: Exception) {
                Log.e("AVATAR", "Error per $googleId: ${e.message}")
            }
        }
    }

    fun loadFriendsToAdd() {
        val s = _uiState.value
        if (!s.currentUserIsAdmin && !s.currentUserIsCreator) {
            viewModelScope.launch { _events.emit(ConversationEvent.NotAdmin(R.string.error_not_admin_add)) }
            return
        }
        _uiState.update { it.copy(isLoadingAddableFriends = true, addableFriends = emptyList()) }
        viewModelScope.launch {
            try {
                val amics = withContext(ioDispatcher) { cargarAmigosUsuario(myGoogleId) }
                val members = _uiState.value.participantGoogleIds.toSet()
                val addable = amics
                    .filter { it.googleId !in members && it.googleId.isNotBlank() }
                    .map { FriendForChat(it.googleId, it.username) }
                _uiState.update { it.copy(isLoadingAddableFriends = false, addableFriends = addable) }
            } catch (e: Exception) {
                Log.e("CHAT_CONV", "Error carregant amics: ${e.message}")
                _uiState.update { it.copy(isLoadingAddableFriends = false, addableFriends = emptyList()) }
                _events.emit(ConversationEvent.Error(R.string.error_loading_friends))
            }
        }
    }

    fun clearAddableFriends() = _uiState.update { it.copy(addableFriends = emptyList()) }

    fun addParticipant(newGoogleId: String, newUsername: String) {
        val s = _uiState.value
        if (newGoogleId.isBlank()) return
        if (!s.currentUserIsAdmin && !s.currentUserIsCreator) {
            viewModelScope.launch { _events.emit(ConversationEvent.NotAdmin(R.string.error_not_admin_add)) }
            return
        }
        _uiState.update { it.copy(isProcessingAdmin = true) }
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { afegirParticipant(chatId, myGoogleId, newGoogleId) }
                _uiState.update { st -> st.copy(
                    participantNames = st.participantNames + newUsername,
                    participantGoogleIds = st.participantGoogleIds + newGoogleId,
                    isProcessingAdmin = false
                )}
                _events.emit(ConversationEvent.Success(R.string.success_participant_added, listOf(newUsername)))
                carregarAvatarSiCal(newGoogleId)
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessingAdmin = false) }
                _events.emit(ConversationEvent.Error(R.string.error_server_operation))
            }
        }
    }

    fun kickParticipant(targetGoogleId: String, targetUsername: String) {
        val s = _uiState.value
        if (targetGoogleId.isBlank()) return
        if (targetGoogleId == s.creatorGoogleId) {
            viewModelScope.launch { _events.emit(ConversationEvent.NotAdmin(R.string.error_kick_creator)) }
            return
        }
        if (!s.currentUserIsAdmin && !s.currentUserIsCreator) {
            viewModelScope.launch { _events.emit(ConversationEvent.NotAdmin(R.string.error_not_admin_kick)) }
            return
        }
        if (targetGoogleId in s.adminGoogleIds && !s.currentUserIsCreator) {
            viewModelScope.launch { _events.emit(ConversationEvent.NotAdmin(R.string.error_not_creator_kick)) }
            return
        }
        _uiState.update { it.copy(isProcessingAdmin = true) }
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { expulsarParticipant(chatId, targetGoogleId, myGoogleId) }
                _uiState.update { st -> st.copy(
                    participantNames = st.participantNames.filterNot { it == targetUsername },
                    participantGoogleIds = st.participantGoogleIds.filterNot { it == targetGoogleId },
                    adminGoogleIds = st.adminGoogleIds - targetGoogleId,
                    isProcessingAdmin = false
                )}
                _events.emit(ConversationEvent.Success(R.string.success_participant_kicked, listOf(targetUsername)))
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessingAdmin = false) }
                _events.emit(ConversationEvent.Error(R.string.error_server_operation))
            }
        }
    }

    fun promoteToAdmin(targetGoogleId: String, targetUsername: String) {
        val s = _uiState.value
        if (targetGoogleId.isBlank()) return
        if (!s.currentUserIsAdmin && !s.currentUserIsCreator) {
            viewModelScope.launch { _events.emit(ConversationEvent.NotAdmin(R.string.error_not_admin_promote)) }
            return
        }
        if (targetGoogleId in s.adminGoogleIds) {
            viewModelScope.launch { _events.emit(ConversationEvent.NotAdmin(R.string.error_already_admin, listOf(targetUsername))) }
            return
        }
        _uiState.update { it.copy(isProcessingAdmin = true) }
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { promocionarAdmin(chatId, targetGoogleId, myGoogleId) }
                _uiState.update { st -> st.copy(
                    adminGoogleIds = st.adminGoogleIds + targetGoogleId,
                    isProcessingAdmin = false
                )}
                _events.emit(ConversationEvent.Success(R.string.success_admin_promoted, listOf(targetUsername)))
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessingAdmin = false) }
                _events.emit(ConversationEvent.Error(R.string.error_server_operation))
            }
        }
    }

    fun revokeAdmin(targetGoogleId: String, targetUsername: String) {
        val s = _uiState.value
        if (targetGoogleId.isBlank()) return
        if (targetGoogleId !in s.adminGoogleIds) {
            viewModelScope.launch { _events.emit(ConversationEvent.NotAdmin(R.string.error_not_admin_revoke, listOf(targetUsername))) }
            return
        }
        if (!s.currentUserIsCreator) {
            viewModelScope.launch { _events.emit(ConversationEvent.NotAdmin(R.string.error_not_creator_revoke)) }
            return
        }
        if (targetGoogleId == s.creatorGoogleId) {
            viewModelScope.launch { _events.emit(ConversationEvent.NotAdmin(R.string.error_revoke_creator)) }
            return
        }
        _uiState.update { it.copy(isProcessingAdmin = true) }
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { revocarAdmin(chatId, targetGoogleId, myGoogleId) }
                _uiState.update { st -> st.copy(
                    adminGoogleIds = st.adminGoogleIds - targetGoogleId,
                    isProcessingAdmin = false
                )}
                _events.emit(ConversationEvent.Success(R.string.success_admin_revoked, listOf(targetUsername)))
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessingAdmin = false) }
                _events.emit(ConversationEvent.Error(R.string.error_server_operation))
            }
        }
    }

    fun exitChat(onLeft: () -> Unit) {
        if (chatId == -1L || myGoogleId.isBlank()) { onLeft(); return }
        _uiState.update { it.copy(isProcessingAdmin = true) }
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { sortirDelXat(chatId, myGoogleId) }
                ChatEventBus.onParticipantLeft(chatId, myUsername)
                onLeft()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CHAT_CONV", "Error sortint del xat: ${e.message}")
                _uiState.update { it.copy(isProcessingAdmin = false) }
                _events.emit(ConversationEvent.Error(R.string.error_server_operation))
            }
        }
    }


    private fun startPolling() {
        launchMessagePolling()
        launchRealtimeEventsObserver()
    }

    private fun launchMessagePolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                refreshMessages()
                delay(POLLING_INTERVAL_MS)
            }
        }
    }

    private fun launchRealtimeEventsObserver() {
        if (realtimeEventsJob?.isActive == true) return
        realtimeEventsJob = viewModelScope.launch {
            ChatEventBus.events.collect { event ->
                if (event.chatId != null && event.chatId != chatId) return@collect
                handleRealtimeEvent(event)
            }
        }
    }

    private suspend fun handleRealtimeEvent(event: ChatRealtimeEvent) {
        when (event) {
            is ChatRealtimeEvent.NewMessage -> refreshMessages()
            is ChatRealtimeEvent.ParticipantLeft -> handleParticipantRealtimeEvent(
                event.username,
                R.string.info_realtime_participant_left,
                R.string.info_realtime_participant_left_unknown
            )
            is ChatRealtimeEvent.ParticipantKicked -> handleParticipantRealtimeEvent(
                event.username,
                R.string.info_realtime_participant_kicked,
                R.string.info_realtime_participant_kicked_unknown
            )
            is ChatRealtimeEvent.ParticipantJoined -> handleParticipantRealtimeEvent(
                event.username,
                R.string.info_realtime_participant_joined,
                R.string.info_realtime_participant_joined_unknown
            )
            is ChatRealtimeEvent.AdminPromoted -> handleAdminRoleRealtimeEvent(event.username, isPromote = true)
            is ChatRealtimeEvent.AdminRevoked -> handleAdminRoleRealtimeEvent(event.username, isPromote = false)
            is ChatRealtimeEvent.GroupCreated -> {  }
        }
    }

    private suspend fun handleParticipantRealtimeEvent(username: String?, stringRes: Int, fallbackRes: Int) {
        val event = if (!username.isNullOrBlank()) {
            ConversationEvent.Info(stringRes, listOf(username))
        } else {
            ConversationEvent.Info(fallbackRes)
        }
        _events.emit(event)
        loadChatDetails()
    }

    private suspend fun handleAdminRoleRealtimeEvent(username: String?, isPromote: Boolean) {
        val targetGid = _uiState.value.participantGoogleIds.firstOrNull { it == username }
        if (targetGid != null) {
            _uiState.update { state ->
                val updatedAdmins = if (isPromote) state.adminGoogleIds + targetGid else state.adminGoogleIds - targetGid
                state.copy(adminGoogleIds = updatedAdmins)
            }
        }

        val stringRes = if (isPromote) R.string.success_admin_promoted else R.string.success_admin_revoked
        val fallbackRes = if (isPromote) R.string.success_admin_promoted_unknown else R.string.success_admin_revoked_unknown

        val event = if (!username.isNullOrBlank()) {
            ConversationEvent.Info(stringRes, listOf(username))
        } else {
            ConversationEvent.Info(fallbackRes)
        }
        _events.emit(event)
    }

    private fun stopPolling() {
        pollingJob?.cancel(); pollingJob = null
        realtimeEventsJob?.cancel(); realtimeEventsJob = null
    }

    private suspend fun refreshMessages() {
        if (chatId == -1L) return
        try {
            val msgs = withContext(ioDispatcher) { obtenirMissatges(chatId) }
            _uiState.update { state ->
                val pending = state.messages.filter { it.isOptimistic }
                state.copy(
                    isLoadingHistory = false, loadFailed = false,
                    messages = msgs.map { it.toUiState() } + pending
                )
            }
            UnreadMessagesStore.clear(chatId)
            viewModelScope.launch { markIncomingAsRead(msgs) }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Log.e("CHAT_CONV", "Error refrescant: ${e.message}")
            _uiState.update { if (it.messages.isEmpty()) it.copy(isLoadingHistory = false, loadFailed = true) else it }
        }
    }

    private fun loadHistory() {
        historyJob = viewModelScope.launch {
            try {
                val msgs = withContext(ioDispatcher) { obtenirMissatges(chatId) }
                _uiState.update {
                    it.copy(isLoadingHistory = false, loadFailed = false,
                        messages = msgs.map { m -> m.toUiState() })
                }
                historyLoaded = true
                updateReadiness()
                startPolling()
                viewModelScope.launch { markIncomingAsRead(msgs) }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingHistory = false, loadFailed = true) }
                historyLoaded = true
                updateReadiness()
            }
        }
    }


    private fun loadChatDetails() {
        if (myGoogleId.isBlank() || chatId == -1L) return
        detailsJob = viewModelScope.launch {
            try {
                val chats = withContext(ioDispatcher) { obtenirXatsUsuari(myGoogleId) }
                val thisChat = chats.firstOrNull { it.id == chatId } ?: return@launch

                updateChatDetailsState(thisChat)
                preloadAvatarsForChat(thisChat)

                detailsLoaded = true
                updateReadiness()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CHAT_CONV", "Error carregant detalls: ${e.message}")
                detailsLoaded = true
                updateReadiness()
            }
        }
    }

    private fun updateChatDetailsState(chat: ChatDto) {
        val isGroup = chat.type == "GROUP"
        val googleIds = chat.participantGoogleIds

        val otherGoogleId = if (!isGroup) {
            googleIds.firstOrNull { it != myGoogleId && it.isNotBlank() }
        } else null

        val creatorId = if (isGroup) {
            chat.creatorGoogleId?.takeIf { it.isNotBlank() }
                ?: googleIds.firstOrNull { it.isNotBlank() }
        } else null

        _uiState.update { state ->
            val backendAdmins = chat.adminGoogleIds.filter { it.isNotBlank() }.toSet()
            val resolvedAdmins = when {
                backendAdmins.isNotEmpty() -> backendAdmins + listOfNotNull(creatorId)
                state.adminGoogleIds.isNotEmpty() -> state.adminGoogleIds + listOfNotNull(creatorId)
                creatorId != null -> setOf(creatorId)
                else -> emptySet()
            }
            state.copy(
                isGroup = isGroup,
                participantNames = chat.participantUsernames,
                participantGoogleIds = googleIds,
                otherParticipantGoogleId = otherGoogleId,
                creatorGoogleId = creatorId,
                adminGoogleIds = resolvedAdmins,
                currentUserIsCreator = (creatorId == myGoogleId),
                currentUserIsAdmin = (myGoogleId in resolvedAdmins)
            )
        }
    }

    private fun preloadAvatarsForChat(chat: ChatDto) {
        val isGroup = chat.type == "GROUP"
        val googleIds = chat.participantGoogleIds

        if (!isGroup) {
            val otherGoogleId = googleIds.firstOrNull { it != myGoogleId && it.isNotBlank() }
            otherGoogleId?.let { carregarAvatarSiCal(it) }
        } else {
            googleIds.filter { it.isNotBlank() && it != myGoogleId }
                .forEach { carregarAvatarSiCal(it) }
        }
    }


    private fun updateReadiness() {
        if (historyLoaded && detailsLoaded) {
            _uiState.update { it.copy(isReady = true) }
        }
    }

    private suspend fun markIncomingAsRead(messages: List<MessageDto>) {
        messages.filter { it.senderGoogleId != myGoogleId && it.id !in alreadyMarkedIds }
            .forEach { msg ->
                try {
                    marcarMissatgeLlegit(chatId, msg.id, myGoogleId)
                    alreadyMarkedIds.add(msg.id)
                } catch (_: Exception) { }
            }
    }

    private fun MessageDto.toUiState() = MessageUiState(
        id = id, content = content, senderGoogleId = senderGoogleId,
        senderUsername = senderUsername, createdAt = formatDate(createdAt),
        isFromMe = senderGoogleId == myGoogleId, isRead = false
    )

    private fun formatDate(date: String): String {
        if (date.isBlank()) return ""
        return try {
            val parts = date.split(" ", "T")
            "${parts[0].substring(5)}, ${if (parts.size > 1) parts[1].take(5) else ""}"
        } catch (_: Exception) { date.take(10) }
    }

    override fun onCleared() {
        super.onCleared()
        if (ChatEventBus.ActiveChatTracker.activeChatId == chatId) ChatEventBus.ActiveChatTracker.activeChatId = null
        stopPolling()
    }
}