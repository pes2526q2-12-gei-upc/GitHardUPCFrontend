package com.safesteps.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safesteps.auth.UserInfo
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
    val isLoadingFilters: Boolean = false,
    val isSavingFilters: Boolean = false
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

        startLoadingFilters(
            googleId = googleId,
            preserveCurrentValues = false
        )
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

                _uiState.update {
                    it.copy(
                        filterValues = loadedFilters.toProfileFiltersUiModel().values,
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
        val currentFilters = ProfileFiltersUiModel(_uiState.value.filterValues)
        val updatedFilters = currentFilters.updated(filterIndex, value)
        if (updatedFilters == currentFilters) {
            return
        }

        _uiState.update {
            it.copy(
                filterValues = updatedFilters.values,
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

                _uiState.update {
                    it.copy(
                        filterValues = persistedFilters.toProfileFiltersUiModel().values,
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
