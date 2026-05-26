package com.safesteps.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safesteps.auth.UserInfo
import com.safesteps.data.ChatDto
import com.safesteps.data.enviarMissatge
import com.safesteps.data.obtenirXatsUsuari
import com.safesteps.domain.RoutePriority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─── UI models ────────────────────────────────────────────────────────────────

data class ShareRouteChatUiState(
    val chatId: Long,
    val displayName: String,
    val isGroup: Boolean,
    val participantCount: Int,
    val isSelected: Boolean = false
)

enum class ShareRouteTab { SHARE, PLAN }

data class ShareRouteUiState(
    val activeTab: ShareRouteTab = ShareRouteTab.SHARE,
    val isLoadingChats: Boolean = false,
    val loadFailed: Boolean = false,
    val chats: List<ShareRouteChatUiState> = emptyList(),
    val query: String = "",
    val scheduledDate: String? = null,
    val isSending: Boolean = false,
    val sendSuccess: Boolean = false,
    val sendError: Boolean = false
) {
    val filteredChats: List<ShareRouteChatUiState>
        get() = if (query.isBlank()) chats
        else chats.filter { it.displayName.contains(query, ignoreCase = true) }

    val selectedCount: Int
        get() = chats.count { it.isSelected }

    val canShare: Boolean
        get() = selectedCount > 0 && !isSending &&
                (activeTab == ShareRouteTab.SHARE || scheduledDate != null)
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class ShareRouteViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val loadChats: suspend (String) -> List<ChatDto> = ::obtenirXatsUsuari
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareRouteUiState())
    val uiState: StateFlow<ShareRouteUiState> = _uiState.asStateFlow()

    private var currentUser: UserInfo? = null
    private var loadJob: Job? = null

    fun onCurrentUserChanged(user: UserInfo?) {
        currentUser = user
    }

    fun onScreenOpened() {
        val googleId = currentUser?.googleId?.takeIf { it.isNotBlank() } ?: return
        loadUserChats(googleId)
    }

    fun onTabSelected(tab: ShareRouteTab) {
        _uiState.update { it.copy(activeTab = tab, sendError = false) }
    }

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun onChatToggled(chatId: Long) {
        _uiState.update { state ->
            state.copy(
                chats = state.chats.map { chat ->
                    if (chat.chatId == chatId) chat.copy(isSelected = !chat.isSelected)
                    else chat
                }
            )
        }
    }

    fun onScheduledDateChanged(isoDate: String?) {
        _uiState.update { it.copy(scheduledDate = isoDate) }
    }

    fun onShareClicked(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        originAddress: String?,
        destAddress: String?,
        distanceText: String,
        durationText: String,
        routePriority: RoutePriority?
    ) {
        val senderGoogleId = currentUser?.googleId?.takeIf { it.isNotBlank() } ?: return
        val state = _uiState.value
        val selectedChats = state.chats.filter { it.isSelected }
        if (selectedChats.isEmpty() || state.isSending) return

        val content = buildRouteMessageContent(
            activeTab = state.activeTab,
            scheduledDate = state.scheduledDate,
            originLat = originLat,
            originLng = originLng,
            destLat = destLat,
            destLng = destLng,
            originAddress = originAddress,
            destAddress = destAddress,
            distanceText = distanceText,
            durationText = durationText,
            routePriority = routePriority
        )

        _uiState.update { it.copy(isSending = true, sendError = false) }

        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    selectedChats.forEach { chat ->
                        enviarMissatge(
                            chatId = chat.chatId,
                            senderGoogleId = senderGoogleId,
                            content = content
                        )
                    }
                }
                _uiState.update { it.copy(isSending = false, sendSuccess = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _uiState.update { it.copy(isSending = false, sendError = true) }
            }
        }
    }

    fun dismissSendError() {
        _uiState.update { it.copy(sendError = false) }
    }

    fun dismissSendSuccess() {
        _uiState.update { it.copy(sendSuccess = false) }
    }

    private fun loadUserChats(googleId: String) {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoadingChats = true, loadFailed = false) }

        loadJob = viewModelScope.launch {
            try {
                val chats = withContext(ioDispatcher) { loadChats(googleId) }
                _uiState.update { state ->
                    state.copy(
                        isLoadingChats = false,
                        loadFailed = false,
                        chats = chats.map { chat ->
                            val isPrivate = chat.type.equals("PRIVATE", ignoreCase = true)
                            val displayName = if (isPrivate) {
                                chat.participantUsernames
                                    .firstOrNull { it != currentUser?.username }
                                    ?: chat.name
                                    ?: "Chat"
                            } else {
                                chat.name ?: "Grup"
                            }
                            ShareRouteChatUiState(
                                chatId = chat.id,
                                displayName = displayName,
                                isGroup = !isPrivate,
                                participantCount = chat.participantUsernames.size
                            )
                        }
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _uiState.update { it.copy(isLoadingChats = false, loadFailed = true) }
            }
        }
    }

    private fun buildRouteMessageContent(
        activeTab: ShareRouteTab,
        scheduledDate: String?,
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        originAddress: String?,
        destAddress: String?,
        distanceText: String,
        durationText: String,
        routePriority: RoutePriority?
    ): String {
        val origin = originAddress?.takeIf { it.isNotBlank() } ?: "Origen"
        val dest = destAddress?.takeIf { it.isNotBlank() } ?: "Destinació"
        val priority = routePriority?.toDisplayName() ?: ""
        val routeTag = "safesteps_route:oLat=$originLat|oLng=$originLng|dLat=$destLat|dLng=$destLng"

        return if (activeTab == ShareRouteTab.PLAN && scheduledDate != null) {
            buildString {
                appendLine("📅 Ha planificat una ruta")
                appendLine("📍 $origin → $dest")
                if (priority.isNotBlank()) appendLine("🛡 $priority")
                appendLine("📏 $distanceText · ⏱ $durationText")
                val formattedDate = try {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.getDefault())
                    val parsed = sdf.parse(scheduledDate)
                    if (parsed != null) java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(parsed) else scheduledDate
                } catch (e: Exception) { scheduledDate }
                appendLine("🗓 $formattedDate")
                append(routeTag)
            }
        } else {
            buildString {
                appendLine("🗺 Ha compartit una ruta")
                appendLine("📍 $origin → $dest")
                if (priority.isNotBlank()) appendLine("🛡 $priority")
                appendLine("📏 $distanceText · ⏱ $durationText")
                append(routeTag)
            }
        }
    }
}

private fun RoutePriority.toDisplayName(): String = when (this) {
    RoutePriority.SAFETY -> "Seguretat"
    RoutePriority.ACCESSIBILITY -> "Confort"
    RoutePriority.HEAT -> "Clima"
    RoutePriority.PERSONALIZED -> "Personalitzat"
}