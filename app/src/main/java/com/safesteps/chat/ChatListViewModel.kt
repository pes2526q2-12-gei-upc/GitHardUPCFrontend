package com.safesteps.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safesteps.R
import com.safesteps.chat.ChatEventBus
import com.safesteps.chat.ChatRealtimeEvent
import com.safesteps.data.ChatDto
import com.safesteps.data.cargarAmigosUsuario
import com.safesteps.data.cargarPerfilDeUsuario
import com.safesteps.data.crearXat
import com.safesteps.data.crearXatPrivat
import com.safesteps.data.obtenirXatsUsuari
import com.safesteps.data.sortirDelXat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatListItemUiState(
    val chatId: Long,
    val otherParticipantName: String,
    val otherParticipantGoogleId: String? = null,
    val type: String,
    val createdAt: String?,
    val participantNames: List<String> = emptyList(),
    val participantGoogleIds: List<String> = emptyList(),
    val avatarUrl: String? = null
)

data class FriendForChat(val googleId: String, val username: String)

data class ChatListUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val loadFailed: Boolean = false,
    val isOpeningChat: Boolean = false,
    val chats: List<ChatListItemUiState> = emptyList(),
    val isLoadingFriends: Boolean = false,
    val friendsForNewChat: List<FriendForChat> = emptyList(),
    val selectedFriendIds: Set<String> = emptySet(),
    val newChatGroupName: String = "",
    val isCreatingChat: Boolean = false
)

sealed class ChatListEvent {
    data class Info(val msgResId: Int, val args: List<Any> = emptyList()) : ChatListEvent()
}

class ChatListViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    private val _avatars = MutableStateFlow<Map<String, String>>(emptyMap())
    val avatars: StateFlow<Map<String, String>> = _avatars.asStateFlow()

    private val _events = MutableSharedFlow<ChatListEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ChatListEvent> = _events.asSharedFlow()

    private var currentGoogleId: String = ""
    private var currentUsername: String = ""
    private var loadChatsJob: Job? = null
    private var structuralEventsJob: Job? = null

    fun onCurrentUserChanged(googleId: String, username: String) {
        currentGoogleId = googleId
        currentUsername = username
    }

    fun onScreenOpened() {
        if (currentGoogleId.isBlank()) return
        observeStructuralEventsIfNeeded()
        loadChats(showLoadingIndicator = _uiState.value.chats.isEmpty())
    }

    fun retryLoad() = onScreenOpened()

    fun loadAvatarIfNeeded(googleId: String) {
        if (googleId.isBlank()) return
        val cached = _avatars.value[googleId]
        if (!cached.isNullOrBlank()) return
        viewModelScope.launch {
            val url = resolveAvatarUrl(googleId)
            _avatars.update { it + (googleId to url.orEmpty()) }
        }
    }

    fun exitChat(chatId: Long) {
        _uiState.update { state -> state.copy(chats = state.chats.filterNot { it.chatId == chatId }) }
        val googleId = currentGoogleId
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { sortirDelXat(chatId, googleId) }
                _events.emit(ChatListEvent.Info(R.string.info_left_chat))
            } catch (e: Exception) {
                Log.e("CHAT_LIST", "Error sortint: ${e.message}")
                loadChats(showLoadingIndicator = false)
            }
        }
    }

    fun findOrCreatePrivateChat(
        friendGoogleId: String, friendUsername: String, onChatReady: (Long) -> Unit
    ) {
        if (currentGoogleId.isBlank()) return
        _uiState.update { it.copy(isOpeningChat = true) }
        viewModelScope.launch {
            try {
                val chatId = withContext(ioDispatcher) {
                    val existing = obtenirXatsUsuari(currentGoogleId)
                    findPrivateChatWith(existing, currentGoogleId, friendGoogleId)?.id
                        ?: crearXatPrivat(currentGoogleId, friendGoogleId).id
                }
                _uiState.update { it.copy(isOpeningChat = false) }
                onChatReady(chatId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(isOpeningChat = false) }
            }
        }
    }

    fun loadFriendsForNewChat() {
        if (currentGoogleId.isBlank()) return
        _uiState.update {
            it.copy(
                isLoadingFriends = true,
                selectedFriendIds = emptySet(),
                newChatGroupName = ""
            )
        }
        viewModelScope.launch {
            try {
                val friends = withContext(ioDispatcher) { cargarAmigosUsuario(currentGoogleId) }
                _uiState.update {
                    it.copy(
                        isLoadingFriends = false,
                        friendsForNewChat = friends.map { f -> FriendForChat(f.googleId, f.username) }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingFriends = false, friendsForNewChat = emptyList()) }
            }
        }
    }

    fun toggleFriendSelection(googleId: String) {
        _uiState.update { state ->
            val sel = state.selectedFriendIds.toMutableSet()
            if (googleId in sel) sel.remove(googleId) else sel.add(googleId)
            state.copy(selectedFriendIds = sel)
        }
    }

    fun onGroupNameChanged(name: String) = _uiState.update { it.copy(newChatGroupName = name) }

    fun createChat(onChatReady: (Long, String) -> Unit) {
        val selectedIds = _uiState.value.selectedFriendIds.toList()
        val groupName = _uiState.value.newChatGroupName.trim()
        val isGroup = selectedIds.size > 1

        if (!isValidChatInput(selectedIds, isGroup, groupName)) return

        _uiState.update { it.copy(isCreatingChat = true) }

        viewModelScope.launch {
            try {
                val chat = requestCreateChat(isGroup, groupName, selectedIds)
                val displayName = determineDisplayName(isGroup, groupName, selectedIds.firstOrNull())

                _uiState.update { it.copy(isCreatingChat = false) }
                onChatReady(chat.id, displayName)
                if (isGroup) ChatEventBus.onGroupCreated(chat.id, groupName)

            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(isCreatingChat = false) }
            }
        }
    }

    private fun isValidChatInput(selectedIds: List<String>, isGroup: Boolean, groupName: String): Boolean {
        if (selectedIds.isEmpty() || currentGoogleId.isBlank()) return false
        if (isGroup && groupName.isBlank()) return false
        return true
    }

    private suspend fun requestCreateChat(isGroup: Boolean, groupName: String, selectedIds: List<String>): ChatDto {
        return crearXat(
            type = if (isGroup) "GROUP" else "PRIVATE",
            name = if (isGroup) groupName else null,
            participantGoogleIds = listOf(currentGoogleId) + selectedIds
        )
    }

    private fun determineDisplayName(isGroup: Boolean, groupName: String, firstFriendId: String?): String {
        if (isGroup) return groupName
        return _uiState.value.friendsForNewChat
            .firstOrNull { it.googleId == firstFriendId }?.username.orEmpty()
    }

    private fun observeStructuralEventsIfNeeded() {
        if (structuralEventsJob?.isActive == true) return
        structuralEventsJob = viewModelScope.launch {
            ChatEventBus.events.collect { event ->
                if (currentGoogleId.isBlank()) return@collect
                val shouldRefresh = when (event) {
                    is ChatRealtimeEvent.GroupCreated,
                    is ChatRealtimeEvent.ParticipantKicked,
                    is ChatRealtimeEvent.ParticipantLeft -> true
                    else -> false
                }
                if (shouldRefresh) loadChats(showLoadingIndicator = false)
            }
        }
    }

    private fun loadChats(showLoadingIndicator: Boolean) {
        if (currentGoogleId.isBlank() || loadChatsJob?.isActive == true) return

        val hasExistingChats = _uiState.value.chats.isNotEmpty()
        _uiState.update {
            it.copy(
                isLoading = showLoadingIndicator && !hasExistingChats,
                isRefreshing = !showLoadingIndicator && hasExistingChats,
                loadFailed = false
            )
        }

        loadChatsJob = viewModelScope.launch {
            try {
                val chats = withContext(ioDispatcher) { obtenirXatsUsuari(currentGoogleId) }
                val chatItems = chats.mapNotNull { chat ->
                    try { resolveChatItem(chat) }
                    catch (e: Exception) {
                        Log.e("CHAT_LIST", "Error resolent xat ${chat.id}: ${e.message}")
                        null
                    }
                }
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, loadFailed = false, chats = chatItems)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CHAT_LIST", "Error: ${e.message}")
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, loadFailed = it.chats.isEmpty())
                }
            }
        }
    }

    private suspend fun resolveChatItem(chat: ChatDto): ChatListItemUiState {
        val usernames = chat.participantUsernames
        val googleIds = chat.participantGoogleIds

        if (chat.type != "PRIVATE") {
            return ChatListItemUiState(
                chatId = chat.id,
                otherParticipantName = chat.name.orEmpty(),
                type = chat.type,
                createdAt = chat.createdAt,
                participantNames = usernames,
                participantGoogleIds = googleIds
            )
        }

        val myIndex = googleIds.indexOf(currentGoogleId)
        val otherGoogleId = googleIds.firstOrNull { it != currentGoogleId && it.isNotBlank() }
        val otherName = if (myIndex >= 0) {
            usernames.indices.firstOrNull { it != myIndex }?.let { usernames[it] }
                ?: usernames.firstOrNull { it != currentUsername }
                ?: chat.name.orEmpty()
        } else {
            usernames.firstOrNull { it != currentUsername } ?: chat.name.orEmpty()
        }

        val photoUrl = otherGoogleId?.let { resolveAvatarUrl(it) }
        if (otherGoogleId != null) {
            _avatars.update { it + (otherGoogleId to photoUrl.orEmpty()) }
        }

        return ChatListItemUiState(
            chatId = chat.id,
            otherParticipantName = otherName,
            otherParticipantGoogleId = otherGoogleId,
            type = chat.type,
            createdAt = chat.createdAt,
            participantNames = usernames,
            participantGoogleIds = googleIds,
            avatarUrl = photoUrl
        )
    }

    private suspend fun resolveAvatarUrl(googleId: String): String? {
        if (googleId.isBlank()) return null
        try {
            val url = withContext(ioDispatcher) { cargarPerfilDeUsuario(googleId) }?.pictureUrl?.trim()
            if (!url.isNullOrBlank()) {
                Log.d("AVATAR", "cargarPerfil($googleId) → $url")
                return url
            }
        } catch (e: Exception) {
            Log.w("AVATAR", "Error cargarPerfil($googleId): ${e.message}")
        }
        return try {
            val amics = withContext(ioDispatcher) { cargarAmigosUsuario(currentGoogleId) }
            val url = amics.firstOrNull { it.googleId == googleId }?.photoUrl?.trim()
            Log.d("AVATAR", "amics($googleId) → $url")
            url
        } catch (_: Exception) { null }
    }

    private fun findPrivateChatWith(
        chats: List<ChatDto>, myGoogleId: String, otherGoogleId: String
    ): ChatDto? = chats.firstOrNull { chat ->
        chat.type == "PRIVATE" &&
                myGoogleId in chat.participantGoogleIds &&
                otherGoogleId in chat.participantGoogleIds
    }

    override fun onCleared() {
        loadChatsJob?.cancel()
        structuralEventsJob?.cancel()
        super.onCleared()
    }
}