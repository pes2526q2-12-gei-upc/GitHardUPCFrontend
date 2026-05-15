package com.safesteps.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val participantNames: List<String> = emptyList()
)

data class FriendForChat(val googleId: String, val username: String)

data class ChatListUiState(
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val isOpeningChat: Boolean = false,
    val chats: List<ChatListItemUiState> = emptyList(),
    val isLoadingFriends: Boolean = false,
    val friendsForNewChat: List<FriendForChat> = emptyList(),
    val selectedFriendIds: Set<String> = emptySet(),
    val newChatGroupName: String = "",
    val isCreatingChat: Boolean = false
)

class ChatListViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    private val _avatars = MutableStateFlow<Map<String, String>>(emptyMap())
    val avatars: StateFlow<Map<String, String>> = _avatars.asStateFlow()

    private var currentGoogleId: String = ""
    private var currentUsername: String = ""

    fun onCurrentUserChanged(googleId: String, username: String) {
        currentGoogleId = googleId; currentUsername = username
    }

    fun onScreenOpened() { if (currentGoogleId.isBlank()) return; loadChats() }
    fun retryLoad() = onScreenOpened()

    fun loadAvatarIfNeeded(googleId: String) {
        if (googleId.isBlank() || _avatars.value.containsKey(googleId)) return
        viewModelScope.launch {
            try {
                val profile = withContext(ioDispatcher) { cargarPerfilDeUsuario(googleId) }
                _avatars.update { it + (googleId to (profile?.pictureUrl.orEmpty())) }
            } catch (_: Exception) { _avatars.update { it + (googleId to "") } }
        }
    }

    fun exitChat(chatId: Long) {
        _uiState.update { state -> state.copy(chats = state.chats.filterNot { it.chatId == chatId }) }
        val googleId = currentGoogleId
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { sortirDelXat(chatId, googleId) }
                Log.d("CHAT_LIST", "Sortida del xat $chatId confirmada")
            } catch (e: Exception) {
                Log.e("CHAT_LIST", "Error sortint: ${e.message}")
                loadChats()
            }
        }
    }

    fun findOrCreatePrivateChat(friendGoogleId: String, friendUsername: String, onChatReady: (Long) -> Unit) {
        if (currentGoogleId.isBlank()) return
        _uiState.update { it.copy(isOpeningChat = true) }
        viewModelScope.launch {
            try {
                val chatId = withContext(ioDispatcher) {
                    val existing = obtenirXatsUsuari(currentGoogleId)
                    findPrivateChatWith(existing, currentGoogleId, currentUsername, friendGoogleId, friendUsername)?.id
                        ?: crearXatPrivat(currentGoogleId, friendGoogleId).id
                }
                _uiState.update { it.copy(isOpeningChat = false) }
                onChatReady(chatId)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { _uiState.update { it.copy(isOpeningChat = false) } }
        }
    }

    fun loadFriendsForNewChat() {
        if (currentGoogleId.isBlank()) return
        _uiState.update { it.copy(isLoadingFriends = true, selectedFriendIds = emptySet(), newChatGroupName = "") }
        viewModelScope.launch {
            try {
                val friends = withContext(ioDispatcher) { cargarAmigosUsuario(currentGoogleId) }
                _uiState.update { it.copy(isLoadingFriends = false, friendsForNewChat = friends.map { f -> FriendForChat(f.googleId, f.username) }) }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { _uiState.update { it.copy(isLoadingFriends = false, friendsForNewChat = emptyList()) } }
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
        val selected = _uiState.value.selectedFriendIds.toList()
        if (selected.isEmpty() || currentGoogleId.isBlank()) return
        val isGroup = selected.size > 1
        val groupName = _uiState.value.newChatGroupName.trim()
        if (isGroup && groupName.isBlank()) return
        _uiState.update { it.copy(isCreatingChat = true) }
        viewModelScope.launch {
            try {
                val chat = withContext(ioDispatcher) {
                    crearXat(
                        type = if (isGroup) "GROUP" else "PRIVATE",
                        name = if (isGroup) groupName else null,
                        participantGoogleIds = listOf(currentGoogleId) + selected
                    )
                }
                val displayName = if (isGroup) groupName else {
                    _uiState.value.friendsForNewChat.firstOrNull { it.googleId == selected.first() }?.username ?: ""
                }
                _uiState.update { it.copy(isCreatingChat = false) }
                onChatReady(chat.id, displayName)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { _uiState.update { it.copy(isCreatingChat = false) } }
        }
    }

    private fun loadChats() {
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                val chats = withContext(ioDispatcher) { obtenirXatsUsuari(currentGoogleId) }
                _uiState.update { it.copy(isLoading = false, chats = chats.map { c -> c.toUiState() }) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                Log.e("CHAT_LIST", "Error: ${e.message}")
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
            }
        }
    }

    private fun ChatDto.toUiState(): ChatListItemUiState {
        val usernames = participantUsernames ?: emptyList()
        val googleIds = participantGoogleIds ?: emptyList()
        val otherGoogleId = googleIds.firstOrNull { it != currentGoogleId }
        val otherName =
            if (type == "PRIVATE") usernames.firstOrNull { it != currentUsername } ?: name ?: "Xat"
            else name ?: usernames.joinToString(", ")
        return ChatListItemUiState(chatId = id, otherParticipantName = otherName,
            otherParticipantGoogleId = otherGoogleId, type = type, createdAt = createdAt, participantNames = usernames)
    }

    private fun findPrivateChatWith(chats: List<ChatDto>, myGoogleId: String, myUsername: String, otherGoogleId: String, otherUsername: String): ChatDto? =
        chats.firstOrNull { chat ->
            if (chat.type != "PRIVATE") return@firstOrNull false
            val ids = (chat.participantGoogleIds ?: emptyList())
            if (ids.isNotEmpty()) myGoogleId in ids && otherGoogleId in ids
            else {
                val names = (chat.participantUsernames ?: emptyList()).toSet()
                myUsername in names && otherUsername in names
            }
        }
}