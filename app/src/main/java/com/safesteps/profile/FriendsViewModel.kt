package com.safesteps.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safesteps.auth.UserInfo
import com.safesteps.data.EmergencyContactProfile
import com.safesteps.data.FriendSearchUser
import com.safesteps.data.aceptarSolicitudAmistad
import com.safesteps.data.cargarAmigosUsuario
import com.safesteps.data.cargarContactosEmergenciaUsuario
import com.safesteps.data.cargarPerfilPublicoUsuario
import com.safesteps.data.cargarSolicitudesPendientesAmistad
import com.safesteps.data.desmarcarContactoEmergenciaUsuario
import com.safesteps.data.denegarSolicitudAmistad
import com.safesteps.data.eliminarAmigoUsuario
import com.safesteps.data.marcarContactoEmergenciaUsuario
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class FriendListItemUiState(
    val googleId: String,
    val username: String,
    val email: String,
    val photoUrl: String? = null,
    val isEmergencyContact: Boolean = false,
    val isUpdatingEmergencyContact: Boolean = false,
    val isRemoving: Boolean = false
)

data class PendingFriendRequestUiState(
    val googleId: String,
    val username: String,
    val email: String,
    val photoUrl: String? = null,
    val isAccepting: Boolean = false,
    val isDeclining: Boolean = false
)

data class FriendsUiState(
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val pendingRequests: List<PendingFriendRequestUiState> = emptyList(),
    val friends: List<FriendListItemUiState> = emptyList()
)

class FriendsViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val loadFriends: suspend (String) -> List<FriendSearchUser> = ::cargarAmigosUsuario,
    private val loadPendingRequests: suspend (String) -> List<FriendSearchUser> =
        ::cargarSolicitudesPendientesAmistad,
    private val loadEmergencyContacts: suspend (String) -> List<EmergencyContactProfile> =
        ::cargarContactosEmergenciaUsuario,
    private val loadFriendPublicProfile: suspend (String) -> EmergencyContactProfile? =
        ::cargarPerfilPublicoUsuario,
    private val acceptFriendRequest: suspend (String, String) -> Unit = ::aceptarSolicitudAmistad,
    private val declineFriendRequest: suspend (String, String) -> Unit = ::denegarSolicitudAmistad,
    private val addEmergencyContact: suspend (String, String) -> Unit =
        ::marcarContactoEmergenciaUsuario,
    private val removeEmergencyContact: suspend (String, String) -> Unit =
        ::desmarcarContactoEmergenciaUsuario,
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

    fun onAcceptFriendRequestClicked(senderGoogleId: String) {
        val receiverGoogleId = currentUser?.googleId?.takeIf { it.isNotBlank() } ?: return
        val request = _uiState.value.pendingRequests.firstOrNull { it.googleId == senderGoogleId } ?: return
        if (request.isAccepting || request.isDeclining) {
            return
        }

        _uiState.update { state ->
            state.copy(
                pendingRequests = state.pendingRequests.map { pending ->
                    if (pending.googleId == senderGoogleId) {
                        pending.copy(isAccepting = true)
                    } else {
                        pending
                    }
                }
            )
        }

        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    acceptFriendRequest(receiverGoogleId, senderGoogleId)
                }

                val acceptedFriend = request.toFriendUiState()
                _uiState.update { state ->
                    state.copy(
                        pendingRequests = state.pendingRequests
                            .filterNot { pending -> pending.googleId == senderGoogleId },
                        friends = (state.friends + acceptedFriend)
                            .distinctBy(FriendListItemUiState::googleId)
                            .sortedBy { friend -> friend.username.lowercase() }
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _uiState.update { state ->
                    state.copy(
                        pendingRequests = state.pendingRequests.map { pending ->
                            if (pending.googleId == senderGoogleId) {
                                pending.copy(isAccepting = false)
                            } else {
                                pending
                            }
                        }
                    )
                }
            }
        }
    }

    fun onDeclineFriendRequestClicked(senderGoogleId: String) {
        val receiverGoogleId = currentUser?.googleId?.takeIf { it.isNotBlank() } ?: return
        val request = _uiState.value.pendingRequests.firstOrNull { it.googleId == senderGoogleId } ?: return
        if (request.isAccepting || request.isDeclining) {
            return
        }

        _uiState.update { state ->
            state.copy(
                pendingRequests = state.pendingRequests.map { pending ->
                    if (pending.googleId == senderGoogleId) {
                        pending.copy(isDeclining = true)
                    } else {
                        pending
                    }
                }
            )
        }

        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    declineFriendRequest(receiverGoogleId, senderGoogleId)
                }

                _uiState.update { state ->
                    state.copy(
                        pendingRequests = state.pendingRequests
                            .filterNot { pending -> pending.googleId == senderGoogleId }
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _uiState.update { state ->
                    state.copy(
                        pendingRequests = state.pendingRequests.map { pending ->
                            if (pending.googleId == senderGoogleId) {
                                pending.copy(isDeclining = false)
                            } else {
                                pending
                            }
                        }
                    )
                }
            }
        }
    }

    fun onRemoveFriendClicked(targetGoogleId: String) {
        val requesterGoogleId = currentUser?.googleId?.takeIf { it.isNotBlank() } ?: return
        val targetFriend = _uiState.value.friends.firstOrNull { it.googleId == targetGoogleId } ?: return
        if (targetFriend.isRemoving || targetFriend.isUpdatingEmergencyContact) {
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

    fun onEmergencyContactToggleClicked(targetGoogleId: String) {
        val requesterGoogleId = currentUser?.googleId?.takeIf { it.isNotBlank() } ?: return
        val targetFriend = _uiState.value.friends.firstOrNull { it.googleId == targetGoogleId } ?: return
        if (targetFriend.isRemoving || targetFriend.isUpdatingEmergencyContact) {
            return
        }

        _uiState.update { state ->
            state.copy(
                friends = state.friends.map { friend ->
                    if (friend.googleId == targetGoogleId) {
                        friend.copy(isUpdatingEmergencyContact = true)
                    } else {
                        friend
                    }
                }
            )
        }

        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    if (targetFriend.isEmergencyContact) {
                        removeEmergencyContact(requesterGoogleId, targetGoogleId)
                    } else {
                        addEmergencyContact(requesterGoogleId, targetGoogleId)
                    }
                }

                _uiState.update { state ->
                    state.copy(
                        friends = state.friends.map { friend ->
                            if (friend.googleId == targetGoogleId) {
                                friend.copy(
                                    isEmergencyContact = !targetFriend.isEmergencyContact,
                                    isUpdatingEmergencyContact = false
                                )
                            } else {
                                friend
                            }
                        }
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _uiState.update { state ->
                    state.copy(
                        friends = state.friends.map { friend ->
                            if (friend.googleId == targetGoogleId) {
                                friend.copy(isUpdatingEmergencyContact = false)
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
                val friendshipLists = withContext(ioDispatcher) {
                    coroutineScope {
                        val friendsDeferred = async { loadFriends(requesterGoogleId) }
                        val pendingDeferred = async { loadPendingRequests(requesterGoogleId) }
                        val emergencyContactsDeferred = async {
                            runCatching {
                                loadEmergencyContacts(requesterGoogleId)
                            }.getOrElse { error ->
                                Log.w(
                                    "FRIENDS_VM",
                                    "No se pudieron cargar los contactos de emergencia.",
                                    error
                                )
                                emptyList()
                            }
                        }
                        val friends = friendsDeferred.await()
                        val emergencyContactIds = resolveEmergencyContactIds(
                            friends = friends,
                            emergencyContacts = emergencyContactsDeferred.await()
                        )
                        FriendshipLists(
                            friends = friends,
                            pendingRequests = pendingDeferred.await(),
                            emergencyContactIds = emergencyContactIds
                        )
                    }
                }

                if (loadVersion != latestLoadVersion || currentUser?.googleId != requesterGoogleId) {
                    return@launch
                }

                _uiState.update {
                    FriendsUiState(
                        isLoading = false,
                        loadFailed = false,
                        pendingRequests = friendshipLists.pendingRequests.map(::toPendingUiState),
                        friends = friendshipLists.friends.map { friend ->
                            toUiState(
                                friend = friend,
                                isEmergencyContact = friend.googleId in friendshipLists.emergencyContactIds
                            )
                        }
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

    private suspend fun resolveEmergencyContactIds(
        friends: List<FriendSearchUser>,
        emergencyContacts: List<EmergencyContactProfile>
    ): Set<String> {
        if (friends.isEmpty() || emergencyContacts.isEmpty()) {
            return emptySet()
        }

        val friendsByUsername = friends.groupBy { it.username.normalizedKey() }
        val resolvedIds = mutableSetOf<String>()
        val ambiguousMatches = mutableListOf<Pair<EmergencyContactProfile, List<FriendSearchUser>>>()

        emergencyContacts.forEach { emergencyContact ->
            val usernameMatches = friendsByUsername[emergencyContact.username.normalizedKey()].orEmpty()
            if (usernameMatches.isEmpty()) {
                return@forEach
            }

            val candidates = if (emergencyContact.photoUrl.normalizedValue() == null) {
                usernameMatches
            } else {
                usernameMatches.filter {
                    it.photoUrl.normalizedValue() == emergencyContact.photoUrl.normalizedValue()
                }.ifEmpty { usernameMatches }
            }

            if (candidates.size == 1) {
                resolvedIds += candidates.single().googleId
            } else {
                ambiguousMatches += emergencyContact to candidates
            }
        }

        if (ambiguousMatches.isEmpty()) {
            return resolvedIds
        }

        val candidatesToResolve = ambiguousMatches
            .flatMap { (_, candidates) -> candidates }
            .distinctBy(FriendSearchUser::googleId)

        val publicProfiles = coroutineScope {
            candidatesToResolve.associate { candidate ->
                candidate.googleId to async {
                    runCatching {
                        loadFriendPublicProfile(candidate.googleId)
                    }.getOrElse { error ->
                        Log.w(
                            "FRIENDS_VM",
                            "No se pudo cargar el perfil publico de ${candidate.googleId}.",
                            error
                        )
                        null
                    }
                }
            }.mapValues { (_, deferred) -> deferred.await() }
        }

        ambiguousMatches.forEach { (emergencyContact, candidates) ->
            val matchedFriend = candidates.firstOrNull { candidate ->
                publicProfiles[candidate.googleId]?.matches(emergencyContact) == true
            }
            if (matchedFriend != null) {
                resolvedIds += matchedFriend.googleId
            }
        }

        return resolvedIds
    }

    private fun toUiState(
        friend: FriendSearchUser,
        isEmergencyContact: Boolean
    ): FriendListItemUiState {
        return FriendListItemUiState(
            googleId = friend.googleId,
            username = friend.username,
            email = friend.email,
            photoUrl = friend.photoUrl,
            isEmergencyContact = isEmergencyContact
        )
    }

    private fun toPendingUiState(friend: FriendSearchUser): PendingFriendRequestUiState {
        return PendingFriendRequestUiState(
            googleId = friend.googleId,
            username = friend.username,
            email = friend.email,
            photoUrl = friend.photoUrl
        )
    }

    private fun PendingFriendRequestUiState.toFriendUiState(): FriendListItemUiState {
        return FriendListItemUiState(
            googleId = googleId,
            username = username,
            email = email,
            photoUrl = photoUrl
        )
    }

    private data class FriendshipLists(
        val friends: List<FriendSearchUser>,
        val pendingRequests: List<FriendSearchUser>,
        val emergencyContactIds: Set<String>
    )

    private fun String.normalizedKey(): String = trim().lowercase(Locale.ROOT)

    private fun String?.normalizedValue(): String? {
        return this
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun EmergencyContactProfile.matches(other: EmergencyContactProfile): Boolean {
        return username.normalizedKey() == other.username.normalizedKey() &&
            photoUrl.normalizedValue() == other.photoUrl.normalizedValue() &&
            points == other.points &&
            level == other.level &&
            createdAt.normalizedValue() == other.createdAt.normalizedValue() &&
            status.normalizedValue() == other.status.normalizedValue()
    }
}
