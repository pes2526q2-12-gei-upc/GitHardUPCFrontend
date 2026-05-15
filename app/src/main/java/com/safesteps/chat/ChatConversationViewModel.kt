package com.safesteps.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safesteps.data.MessageDto
import com.safesteps.data.buscarUsuariosParaAmistad
import com.safesteps.data.cargarAmigosUsuario
import com.safesteps.data.cargarPerfilDeUsuario
import com.safesteps.data.enviarMissatge
import com.safesteps.data.marcarMissatgeLlegit
import com.safesteps.data.obtenirDataActual
import com.safesteps.data.obtenirMissatges
import com.safesteps.chat.ChatEventBus
import com.safesteps.data.obtenirXatsUsuari
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val localId: String? = null
)

data class ConversationUiState(
    val isLoadingHistory: Boolean = true,
    val loadFailed: Boolean = false,
    val messages: List<MessageUiState> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
    val sendFailed: Boolean = false,
    val otherParticipantName: String = "",
    val isGroup: Boolean = false,
    val participantNames: List<String> = emptyList()
)

private const val POLLING_INTERVAL_MS = 3_000L

class ConversationViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private val _avatars = MutableStateFlow<Map<String, String>>(emptyMap())
    val avatars: StateFlow<Map<String, String>> = _avatars.asStateFlow()

    private var chatId: Long = -1
    private var myGoogleId: String = ""
    private var myUsername: String = ""
    private var pollingJob: Job? = null

    fun init(chatId: Long, myGoogleId: String, myUsername: String, otherParticipantName: String) {
        if (this.chatId == chatId && this.myGoogleId == myGoogleId) return
        this.chatId = chatId
        this.myGoogleId = myGoogleId
        this.myUsername = myUsername
        _uiState.update { it.copy(otherParticipantName = otherParticipantName) }
        loadHistory()
        loadChatDetails()
    }

    fun onScreenVisible() { startPolling() }
    fun onScreenHidden() { stopPolling() }


    fun carregarAvatarSiCal(googleId: String) {
        // Si ja el tenim al mapa o el ID és buit, no fem res
        if (googleId.isBlank() || _avatars.value.containsKey(googleId)) return

        viewModelScope.launch {
            try {
                // FEM SERVIR LA MATEIXA FUNCIÓ QUE A FRIENDS
                // Busquem l'usuari a la llista d'amics, que sabem que té photoUrl
                val amics = withContext(ioDispatcher) { cargarAmigosUsuario(myGoogleId) }
                val amicTrobat = amics.find { it.googleId == googleId }

                if (amicTrobat != null && !amicTrobat.photoUrl.isNullOrBlank()) {
                    Log.d("AVATAR_FIX", "Foto trobada via Friends per a $googleId: ${amicTrobat.photoUrl}")
                    _avatars.update { it + (googleId to amicTrobat.photoUrl) }
                } else {
                    // Si no és amic o no té foto, intentem la cerca general (que també funciona a Friends)
                    val resultatsCerca = withContext(ioDispatcher) { buscarUsuariosParaAmistad(googleId) }
                    val usuariCerca = resultatsCerca.firstOrNull { it.googleId == googleId }

                    val urlFinal = usuariCerca?.photoUrl

                    if (!urlFinal.isNullOrBlank()) {
                        Log.d("AVATAR_FIX", "Foto trobada via Cerca per a $googleId: $urlFinal")
                        _avatars.update { it + (googleId to urlFinal) }
                    } else {
                        // Si realment no hi ha foto enlloc, posem una cadena buida perquè no ho torni a intentar
                        Log.w("AVATAR_FIX", "No s'ha trobat cap foto per a $googleId en cap endpoint")
                        _avatars.update { it + (googleId to "") }
                    }
                }
            } catch (e: Exception) {
                Log.e("AVATAR_FIX", "Error en la cascada de càrrega per a $googleId: ${e.message}")
                _avatars.update { it + (googleId to "") }
            }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text, sendFailed = false) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isSending || chatId == -1L || myGoogleId.isBlank()) return
        val localId = UUID.randomUUID().toString()
        val optimistic = MessageUiState(
            id = -1L, content = text,
            senderGoogleId = myGoogleId, senderUsername = myUsername,
            createdAt = formatDate(obtenirDataActual()),
            isFromMe = true, isOptimistic = true, localId = localId
        )
        _uiState.update { it.copy(messages = it.messages + optimistic, inputText = "", isSending = true, sendFailed = false) }
        viewModelScope.launch {
            try {
                val sent = withContext(ioDispatcher) { enviarMissatge(chatId, myGoogleId, text) }
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { if (it.localId == localId) sent.toUiState() else it },
                        isSending = false
                    )
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                Log.e("CHAT_CONV", "Error enviant: ${e.message}")
                _uiState.update { state ->
                    state.copy(messages = state.messages.filterNot { it.localId == localId },
                        isSending = false, sendFailed = true, inputText = text)
                }
            }
        }
    }

    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch { while (true) { refreshMessages(); delay(POLLING_INTERVAL_MS) } }
        // Escolta el WebSocket EventBus per a refresh immediat (redueix latència de 3s a ~0s)
        viewModelScope.launch {
            ChatEventBus.newMessageEvent.collect {
                refreshMessages()
            }
        }
    }

    private fun stopPolling() { pollingJob?.cancel(); pollingJob = null }

    private suspend fun refreshMessages() {
        if (chatId == -1L) return
        try {
            val msgs = withContext(ioDispatcher) { obtenirMissatges(chatId) }
            val others = msgs.filter { it.senderGoogleId != myGoogleId && it.senderGoogleId.isNotBlank() }
            if (others.isNotEmpty()) {
                Log.d("CHAT_MSG", "Rebuts ${msgs.size} missatges: ${others.size} d'altres (${others.map { it.senderGoogleId }.distinct()})")
            }
            _uiState.update { state ->
                val pending = state.messages.filter { it.isOptimistic }
                state.copy(isLoadingHistory = false, loadFailed = false, messages = msgs.map { it.toUiState() } + pending)
            }
            viewModelScope.launch { markIncomingAsRead(msgs) }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            Log.e("CHAT_CONV", "Error refrescant: ${e.message}")
        }
    }

    private fun loadHistory() {
        _uiState.update { it.copy(isLoadingHistory = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                val msgs = withContext(ioDispatcher) { obtenirMissatges(chatId) }
                _uiState.update { it.copy(isLoadingHistory = false, messages = msgs.map { m -> m.toUiState() }) }
                startPolling()
                launch { markIncomingAsRead(msgs) }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { _uiState.update { it.copy(isLoadingHistory = false, loadFailed = true) } }
        }
    }

    private fun loadChatDetails() {
        if (myGoogleId.isBlank() || chatId == -1L) return
        viewModelScope.launch {
            try {
                val chats = withContext(ioDispatcher) { obtenirXatsUsuari(myGoogleId) }
                val thisChat = chats.firstOrNull { it.id == chatId } ?: return@launch
                _uiState.update {
                    it.copy(
                        isGroup = thisChat.type == "GROUP",
                        participantNames = thisChat.participantUsernames ?: emptyList()
                    )
                }
            } catch (e: Exception) {
                Log.e("CHAT_CONV", "Error carregant detalls: ${e.message}")
            }
        }
    }

    private suspend fun markIncomingAsRead(messages: List<MessageDto>) {
        messages.filter { it.senderGoogleId != myGoogleId }.forEach { msg ->
            try { marcarMissatgeLlegit(chatId, msg.id, myGoogleId) } catch (e: Exception) {
                Log.e("CHAT_CONV", "Error marcant com llegit: ${e.message}")
            }
        }
    }

    private fun MessageDto.toUiState() = MessageUiState(
        id = id,
        content = content,
        senderGoogleId = senderGoogleId,
        senderUsername = senderUsername,
        createdAt = formatDate(createdAt),
        isFromMe = senderGoogleId == myGoogleId
    )

    private fun formatDate(date: String): String {
        if (date.isBlank()) return ""
        return try {
            val parts = date.split(" ", "T")
            "${parts[0].substring(5)}, ${if (parts.size > 1) parts[1].take(5) else ""}"
        } catch (_: Exception) { date.take(10) }
    }

    override fun onCleared() { super.onCleared(); stopPolling() }
}