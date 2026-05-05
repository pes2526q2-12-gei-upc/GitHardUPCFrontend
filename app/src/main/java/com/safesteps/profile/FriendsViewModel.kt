package com.safesteps.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safesteps.auth.UserInfo
import com.safesteps.data.FriendSearchUser
import com.safesteps.data.cargarAmigosUsuario
import com.safesteps.data.eliminarAmigoUsuario
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

data class FriendListItemUiState(
    val googleId: String,
    val username: String,
    val email: String,
    val photoUrl: String? = null,
    val isRemoving: Boolean = false
)

data class FriendsUiState(
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val friends: List<FriendListItemUiState> = emptyList()
)

class FriendsViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val loadFriends: suspend (String) -> List<FriendSearchUser> = ::cargarAmigosUsuario,
    private val removeFriend: suspend (String, String) -> Unit = ::eliminarAmigoUsuario
) : ViewModel() {
    private val _uiState = MutableStateFlow(FriendsUiState(isLoading = true))
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    private var currentUser: UserInfo? = null
    private var loadJob: Job? = null
    private var latestLoadVersion = 0L

    fun onCurrentUserChanged(user: UserInfo?) {
        val newGoogleId = user?.googleId?.takeIf { it.isNotBlank() }
        val currentGoogleId = currentUser?.googleId?.takeIf { it.isNotBlank() }
        currentUser = user

        if (newGoogleId == currentGoogleId) {
            return
        }

        loadJob?.cancel()
        latestLoadVersion += 1
        _uiState.value = if (newGoogleId == null) {
            FriendsUiState(isLoading = false)
        } else {
            FriendsUiState(isLoading = true)
        }
    }

    fun onScreenOpened() {
        val requesterGoogleId = currentUser?.googleId?.takeIf { it.isNotBlank() } ?: return
        startLoadingFriends(requesterGoogleId)
    }

    fun retryLoad() {
        onScreenOpened()
    }

    fun onRemoveFriendClicked(targetGoogleId: String) {
        val requesterGoogleId = currentUser?.googleId?.takeIf { it.isNotBlank() } ?: return
        val targetFriend = _uiState.value.friends.firstOrNull { it.googleId == targetGoogleId } ?: return
        if (targetFriend.isRemoving) {
            return
        }

        _uiState.update { state ->
            state.copy(
                friends = state.friends.map { friend ->
                    if (friend.googleId == targetGoogleId) {
                        friend.copy(isRemoving = true)
                    } else {
                        friend
                    }
                }
            )
        }

        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    removeFriend(requesterGoogleId, targetGoogleId)
                }
                _uiState.update { state ->
                    state.copy(
                        friends = state.friends.filterNot { friend -> friend.googleId == targetGoogleId }
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _uiState.update { state ->
                    state.copy(
                        friends = state.friends.map { friend ->
                            if (friend.googleId == targetGoogleId) {
                                friend.copy(isRemoving = false)
                            } else {
                                friend
                            }
                        }
                    )
                }
            }
        }
    }

    private fun startLoadingFriends(requesterGoogleId: String) {
        loadJob?.cancel()
        val loadVersion = ++latestLoadVersion
        _uiState.update { state ->
            state.copy(
                isLoading = true,
                loadFailed = false
            )
        }

        loadJob = viewModelScope.launch {
            try {
                val friends = withContext(ioDispatcher) {
                    loadFriends(requesterGoogleId)
                }

                if (loadVersion != latestLoadVersion || currentUser?.googleId != requesterGoogleId) {
                    return@launch
                }

                _uiState.update {
                    FriendsUiState(
                        isLoading = false,
                        loadFailed = false,
                        friends = friends.map(::toUiState)
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                if (loadVersion != latestLoadVersion || currentUser?.googleId != requesterGoogleId) {
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadFailed = true
                    )
                }
            }
        }
    }

    private fun toUiState(friend: FriendSearchUser): FriendListItemUiState {
        return FriendListItemUiState(
            googleId = friend.googleId,
            username = friend.username,
            email = friend.email,
            photoUrl = friend.photoUrl
        )
    }
}
