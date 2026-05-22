package com.safesteps.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safesteps.auth.UserInfo
import com.safesteps.data.PremiResponse
import com.safesteps.data.RouteCompletionResponse
import com.safesteps.data.UserFilters
import com.safesteps.data.actualizarFiltrosUsuario
import com.safesteps.data.cargarFiltrosUsuario
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

data class ProfileUiState(
    val filterValues: List<Int> = ProfileFiltersUiModel().values,
    val filterEnabledStates: List<Boolean> = ProfileFiltersUiModel().enabledStates,
    val isLoadingFilters: Boolean = false,
    val isSavingFilters: Boolean = false,
    val isLoadingProfile: Boolean = false,
    val level: Long = 1,
    val points: Long = 0,
    val reputacio: Double = 0.0,
    val recompenses: Long = 0,
    val premis: List<PremiResponse> = emptyList(),
    val showLevelUpAnimation: Boolean = false,
    val newLevel: Long = 1,
    val showPrizeAnimation: Boolean = false,
    val lastOpenedPrize: PremiResponse? = null,
    val avatarCatalog: List<String> = allAvatarIds,
    val colorCatalog: List<ColorPrizeEntry> = allColorPrizes,
    val labelCatalog: List<LabelEntry> = allLabels
)

class ProfileViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val loadFilters: suspend (String) -> UserFilters = ::cargarFiltrosUsuario,
    private val saveFilters: suspend (String, UserFilters) -> UserFilters = ::actualizarFiltrosUsuario
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var currentUser: UserInfo? = null
    private var loadJob: Job? = null
    private var saveJob: Job? = null
    private var latestLoadVersion = 0L
    private var latestSaveVersion = 0L
    private var refreshAfterSave = false


    fun onProfileScreenOpened() {
        val googleId = currentUser?.googleId?.takeIf { it.isNotBlank() } ?: return
        loadUserProfile(googleId)
    }

    fun onRouteCompleted(response: RouteCompletionResponse) {
        Log.d("PROFILE_VM", "onRouteCompleted: levelUpdated=${response.levelUpdated}, level=${response.level}, recompenses=${response.recompenses}")

        _uiState.update { state ->
            state.copy(
                level = response.level ?: state.level,
                points = response.totalPoints ?: state.points,
                recompenses = response.recompenses ?: state.recompenses,
                showLevelUpAnimation = response.levelUpdated == true,
                newLevel = response.level ?: state.level
            )
        }
    }

    fun dismissLevelUpAnimation() {
        _uiState.update { it.copy(showLevelUpAnimation = false) }
    }

    fun onCurrentUserChanged(user: UserInfo?) {
        val googleId = user?.googleId?.takeIf { it.isNotBlank() }
        val currentGoogleId = currentUser?.googleId?.takeIf { it.isNotBlank() }
        if (googleId == currentGoogleId) {
            currentUser = user
            return
        }

        currentUser = user
        loadJob?.cancel()
        saveJob?.cancel()
        refreshAfterSave = false
        latestLoadVersion += 1
        latestSaveVersion += 1

        if (googleId == null) {
            _uiState.value = ProfileUiState()
            return
        }

        // 1. Cargamos los filtros (lo que ya hacía)
        startLoadingFilters(
            googleId = googleId,
            preserveCurrentValues = false
        )

        // 2. ¡NUEVO! Cargamos los datos de gamificación
        loadUserProfile(googleId)
    }

    // Añade esta nueva función justo debajo de onCurrentUserChanged:
    private fun loadUserProfile(googleId: String) {
        _uiState.update { it.copy(isLoadingProfile = true) }

        viewModelScope.launch {
            try {
                // Importa cargarPerfilDeUsuario si te lo pide Android Studio
                val userProfile = withContext(ioDispatcher) {
                    com.safesteps.data.cargarPerfilDeUsuario(googleId)
                }

                if (userProfile != null) {
                    _uiState.update { state ->
                        state.copy(
                            isLoadingProfile = false,
                            level = userProfile.level ?: 1,
                            points = userProfile.points ?: 0,
                            reputacio = userProfile.reputacio ?: 0.0,
                            recompenses = userProfile.recompenses ?: 0,
                            premis = userProfile.premis ?: emptyList()
                        )
                    }
                    Log.d("PROFILE_VM", "Perfil cargado: Nivel ${userProfile.level}, Puntos: ${userProfile.points}")
                } else {
                    _uiState.update { it.copy(isLoadingProfile = false) }
                }
            } catch (e: Exception) {
                Log.e("PROFILE_VM", "Error cargando el perfil", e)
                _uiState.update { it.copy(isLoadingProfile = false) }
            }
        }
    }

    fun onFiltersScreenOpened() {
        val googleId = currentUser?.googleId?.takeIf { it.isNotBlank() } ?: return

        if (saveJob?.isActive == true) {
            refreshAfterSave = true
            _uiState.update {
                it.copy(
                    isLoadingFilters = true,
                    isSavingFilters = true
                )
            }
            return
        }

        startLoadingFilters(
            googleId = googleId,
            preserveCurrentValues = true
        )
    }

    private fun startLoadingFilters(
        googleId: String,
        preserveCurrentValues: Boolean
    ) {
        loadJob?.cancel()
        val loadVersion = ++latestLoadVersion

        _uiState.update { state ->
            if (preserveCurrentValues) {
                state.copy(
                    isLoadingFilters = true,
                    isSavingFilters = false
                )
            } else {
                ProfileUiState(isLoadingFilters = true)
            }
        }

        loadJob = viewModelScope.launch {
            try {
                val loadedFilters = withContext(ioDispatcher) {
                    loadFilters(googleId)
                }

                if (loadVersion != latestLoadVersion || currentUser?.googleId != googleId) {
                    return@launch
                }

                val loadedUiModel = loadedFilters.toProfileFiltersUiModel()
                _uiState.update {
                    it.copy(
                        filterValues = loadedUiModel.values,
                        filterEnabledStates = loadedUiModel.enabledStates,
                        isLoadingFilters = false,
                        isSavingFilters = false
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (loadVersion != latestLoadVersion || currentUser?.googleId != googleId) {
                    return@launch
                }

                Log.e(
                    "PROFILE_VIEW_MODEL",
                    "No se pudieron cargar los filtros del usuario",
                    error
                )
                _uiState.update { it.copy(isLoadingFilters = false, isSavingFilters = false) }
            }
        }
    }

    fun onFilterValueChanged(filterIndex: Int, value: Int) {
        val user = currentUser ?: return
        val googleId = user.googleId.takeIf { it.isNotBlank() } ?: return
        val currentFilters = currentUiFilters()
        val updatedFilters = currentFilters.updated(filterIndex, value)
        if (updatedFilters == currentFilters) {
            return
        }

        _uiState.update {
            it.copy(
                filterValues = updatedFilters.values,
                filterEnabledStates = updatedFilters.enabledStates,
                isSavingFilters = true
            )
        }

        saveJob?.cancel()
        refreshAfterSave = false
        latestSaveVersion += 1
        val saveVersion = latestSaveVersion

        saveJob = viewModelScope.launch {
            try {
                val persistedFilters = withContext(ioDispatcher) {
                    saveFilters(googleId, updatedFilters.toUserFilters())
                }

                if (saveVersion != latestSaveVersion || currentUser?.googleId != googleId) {
                    return@launch
                }

                val persistedUiModel = mergePersistedUiModel(
                    persistedFilters = persistedFilters,
                    fallbackUiModel = updatedFilters
                )
                _uiState.update {
                    it.copy(
                        filterValues = persistedUiModel.values,
                        filterEnabledStates = persistedUiModel.enabledStates,
                        isSavingFilters = false
                    )
                }

                maybeRefreshAfterSave(googleId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (saveVersion != latestSaveVersion || currentUser?.googleId != googleId) {
                    return@launch
                }

                Log.e(
                    "PROFILE_VIEW_MODEL",
                    "No se pudieron guardar los filtros del usuario",
                    error
                )
                _uiState.update { it.copy(isSavingFilters = false) }
                maybeRefreshAfterSave(googleId)
            }
        }
    }

    fun onFilterEnabledChanged(filterIndex: Int, enabled: Boolean) {
        val user = currentUser ?: return
        val googleId = user.googleId.takeIf { it.isNotBlank() } ?: return
        val currentFilters = currentUiFilters()
        val updatedFilters = currentFilters.updatedEnabledState(filterIndex, enabled)
        if (updatedFilters == currentFilters) {
            return
        }

        _uiState.update {
            it.copy(
                filterValues = updatedFilters.values,
                filterEnabledStates = updatedFilters.enabledStates,
                isSavingFilters = true
            )
        }

        saveJob?.cancel()
        refreshAfterSave = false
        latestSaveVersion += 1
        val saveVersion = latestSaveVersion

        saveJob = viewModelScope.launch {
            try {
                val persistedFilters = withContext(ioDispatcher) {
                    saveFilters(googleId, updatedFilters.toUserFilters())
                }

                if (saveVersion != latestSaveVersion || currentUser?.googleId != googleId) {
                    return@launch
                }

                val persistedUiModel = mergePersistedUiModel(
                    persistedFilters = persistedFilters,
                    fallbackUiModel = updatedFilters
                )
                _uiState.update {
                    it.copy(
                        filterValues = persistedUiModel.values,
                        filterEnabledStates = persistedUiModel.enabledStates,
                        isSavingFilters = false
                    )
                }

                maybeRefreshAfterSave(googleId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (saveVersion != latestSaveVersion || currentUser?.googleId != googleId) {
                    return@launch
                }

                Log.e(
                    "PROFILE_VIEW_MODEL",
                    "No se pudieron guardar los filtros del usuario",
                    error
                )
                _uiState.update { it.copy(isSavingFilters = false) }
                maybeRefreshAfterSave(googleId)
            }
        }
    }

    fun openPrize() {
        val googleId = currentUser?.googleId?.takeIf { it.isNotBlank() } ?: return
        if (_uiState.value.recompenses <= 0) return

        Log.d("PROFILE_VM", "openPrize: calling backend...")

        viewModelScope.launch {
            try {
                val prize = withContext(ioDispatcher) {
                    com.safesteps.data.abrirPremioEnBackend(googleId)
                }
                Log.d("PROFILE_VM", "openPrize: backend response = $prize")
                if (prize != null) {
                    val isDuplicate = prize.name == "XP"
                    _uiState.update { state ->
                        state.copy(
                            recompenses = (state.recompenses - 1).coerceAtLeast(0),
                            premis = if (isDuplicate) state.premis else state.premis + prize,
                            showPrizeAnimation = true,
                            lastOpenedPrize = prize
                        )
                    }
                    Log.d("PROFILE_VM", "openPrize: animation triggered, prize id=${prize.id}")
                } else {
                    Log.e("PROFILE_VM", "openPrize: backend returned null")
                }
            } catch (e: Exception) {
                Log.e("PROFILE_VM", "openPrize: error", e)
            }
        }
    }

    fun dismissPrizeAnimation() {
        _uiState.update { it.copy(showPrizeAnimation = false, lastOpenedPrize = null) }
    }

    private fun currentUiFilters(): ProfileFiltersUiModel {
        return ProfileFiltersUiModel(
            values = _uiState.value.filterValues,
            enabledStates = _uiState.value.filterEnabledStates
        )
    }

    private fun mergePersistedUiModel(
        persistedFilters: UserFilters,
        fallbackUiModel: ProfileFiltersUiModel
    ): ProfileFiltersUiModel {
        val persistedUiModel = persistedFilters.toProfileFiltersUiModel()
        return ProfileFiltersUiModel(
            values = persistedUiModel.values.mapIndexed { index, persistedValue ->
                if (persistedUiModel.enabledStates[index]) {
                    persistedValue
                } else {
                    fallbackUiModel.values[index]
                }
            },
            enabledStates = persistedUiModel.enabledStates
        )
    }

    private fun maybeRefreshAfterSave(googleId: String) {
        if (!refreshAfterSave || currentUser?.googleId != googleId) {
            return
        }

        refreshAfterSave = false
        startLoadingFilters(
            googleId = googleId,
            preserveCurrentValues = true
        )
    }
}

