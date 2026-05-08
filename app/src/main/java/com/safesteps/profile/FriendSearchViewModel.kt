package com.safesteps.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safesteps.auth.UserInfo
import com.safesteps.data.FriendSearchUser
import com.safesteps.data.buscarUsuariosParaAmistad
import com.safesteps.data.cargarAmigosUsuario
import com.safesteps.data.cargarSolicitudesPendientesAmistad
import com.safesteps.data.enviarSolicitudAmistad
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FriendSearchResultUiState(
    val googleId: String,
    val username: String,
    val email: String,
    val photoUrl: String? = null,
    val isAdded: Boolean = false,
    val isRequestSent: Boolean = false,
    val isSubmittingRequest: Boolean = false
)

data class FriendSearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val searchFailed: Boolean = false,
    val results: List<FriendSearchResultUiState> = emptyList()
)

class FriendSearchViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val searchUsers: suspend (String) -> List<FriendSearchUser> =
        ::buscarUsuariosParaAmistad,
    private val loadFriends: suspend (String) -> List<FriendSearchUser> = ::cargarAmigosUsuario,
    private val loadPendingRequests: suspend (String) -> List<FriendSearchUser> =
        ::cargarSolicitudesPendientesAmistad,
    private val addFriend: suspend (String, String) -> Unit = ::enviarSolicitudAmistad
) : ViewModel() {
    private val _uiState = MutableStateFlow(FriendSearchUiState())
    val uiState: StateFlow<FriendSearchUiState> = _uiState.asStateFlow()

    private var currentUser: UserInfo? = null
    private var searchJob: Job? = null
    private var latestSearchVersion = 0L
    private var pendingFriendRequestIds: Set<String> = emptySet()

    fun onCurrentUserChanged(user: UserInfo?) {
        val newGoogleId = user?.googleId?.takeIf { it.isNotBlank() }
        val currentGoogleId = currentUser?.googleId?.takeIf { it.isNotBlank() }
        currentUser = user

        if (newGoogleId == currentGoogleId) {
            return
        }

        searchJob?.cancel()
        latestSearchVersion += 1
        pendingFriendRequestIds = emptySet()
        _uiState.value = FriendSearchUiState()
    }

    fun onQueryChanged(query: String) {
        _uiState.update {
            it.copy(
                query = query,
                searchFailed = false
            )
        }
        scheduleSearch()
    }

    fun retrySearch() {
        scheduleSearch(debounceMillis = 0L)
    }

    fun onScreenOpened() {
        if (_uiState.value.query.isBlank()) {
            return
        }
        scheduleSearch(debounceMillis = 0L)
    }

    fun onAddFriendClicked(targetGoogleId: String) {
        val requesterGoogleId = currentUser?.googleId?.takeIf { it.isNotBlank() } ?: return
        val targetUser = _uiState.value.results.firstOrNull { it.googleId == targetGoogleId } ?: return
        if (targetUser.isAdded || targetUser.isRequestSent || targetUser.isSubmittingRequest) {
            return
        }

        _uiState.update { state ->
            state.copy(
                results = state.results.map { result ->
                    if (result.googleId == targetGoogleId) {
                        result.copy(isSubmittingRequest = true)
                    } else {
                        result
                    }
                }
            )
        }

        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    addFriend(requesterGoogleId, targetGoogleId)
                }
                pendingFriendRequestIds = pendingFriendRequestIds + targetGoogleId
                _uiState.update { state ->
                    state.copy(
                        results = state.results.map { result ->
                            if (result.googleId == targetGoogleId) {
                                result.copy(
                                    isAdded = false,
                                    isRequestSent = true,
                                    isSubmittingRequest = false
                                )
                            } else {
                                result
                            }
                        }
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _uiState.update { state ->
                    state.copy(
                        results = state.results.map { result ->
                            if (result.googleId == targetGoogleId) {
                                result.copy(isSubmittingRequest = false)
                            } else {
                                result
                            }
                        }
                    )
                }
            }
        }
    }

    private fun scheduleSearch(debounceMillis: Long = 350L) {
        searchJob?.cancel()
        val query = _uiState.value.query
        val normalizedQuery = query.trim()

        if (normalizedQuery.isBlank()) {
            _uiState.update {
                it.copy(
                    isSearching = false,
                    hasSearched = false,
                    searchFailed = false,
                    results = emptyList()
                )
            }
            return
        }

        val searchVersion = ++latestSearchVersion
        _uiState.update { it.copy(isSearching = true, searchFailed = false) }

        searchJob = viewModelScope.launch {
            try {
                if (debounceMillis > 0L) {
                    delay(debounceMillis)
                }

                val users = withContext(ioDispatcher) {
                    val requesterGoogleId = currentUser?.googleId?.takeIf { it.isNotBlank() }
                    if (requesterGoogleId == null) {
                        SearchSnapshot(
                            users = searchUsers(normalizedQuery),
                            acceptedFriendIds = emptySet(),
                            sentRequestIds = emptySet()
                        )
                    } else {
                        coroutineScope {
                            val usersDeferred = async { searchUsers(normalizedQuery) }
                            val friendsDeferred = async { loadFriends(requesterGoogleId) }
                            val searchedUsers = usersDeferred.await()
                            val visibleUsers = searchedUsers.filterNot { user ->
                                user.googleId == requesterGoogleId ||
                                    user.email.equals(currentUser?.email, ignoreCase = true)
                            }
                            val acceptedFriendIds = friendsDeferred.await()
                                .map(FriendSearchUser::googleId)
                                .toSet()
                            val sentRequestIds = visibleUsers
                                .asSequence()
                                .filterNot { user -> user.googleId in acceptedFriendIds }
                                .map(FriendSearchUser::googleId)
                                .distinct()
                                .map { targetGoogleId ->
                                    async {
                                        val pendingRequests = loadPendingRequests(targetGoogleId)
                                        targetGoogleId.takeIf {
                                            pendingRequests.any { request ->
                                                request.googleId == requesterGoogleId
                                            }
                                        }
                                    }
                                }
                                .toList()
                                .awaitAll()
                                .filterNotNull()
                                .toSet()
                            SearchSnapshot(
                                users = visibleUsers,
                                acceptedFriendIds = acceptedFriendIds,
                                sentRequestIds = sentRequestIds
                            )
                        }
                    }
                }

                if (
                    searchVersion != latestSearchVersion ||
                    _uiState.value.query.trim() != normalizedQuery
                ) {
                    return@launch
                }

                pendingFriendRequestIds = users.sentRequestIds
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        hasSearched = true,
                        searchFailed = false,
                        results = users.users
                            .map {
                                user -> toUiState(
                                    user = user,
                                    acceptedFriendIds = users.acceptedFriendIds,
                                    sentRequestIds = users.sentRequestIds
                                )
                            }
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                if (searchVersion != latestSearchVersion) {
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        isSearching = false,
                        hasSearched = true,
                        searchFailed = true,
                        results = emptyList()
                    )
                }
            }
        }
    }

    private fun toUiState(
        user: FriendSearchUser,
        acceptedFriendIds: Set<String>,
        sentRequestIds: Set<String>
    ): FriendSearchResultUiState {
        val isAdded = user.googleId in acceptedFriendIds
        return FriendSearchResultUiState(
            googleId = user.googleId,
            username = user.username,
            email = user.email,
            photoUrl = user.photoUrl,
            isAdded = isAdded,
            isRequestSent = !isAdded && user.googleId in sentRequestIds
        )
    }

    private data class SearchSnapshot(
        val users: List<FriendSearchUser>,
        val acceptedFriendIds: Set<String>,
        val sentRequestIds: Set<String>
    )
}
