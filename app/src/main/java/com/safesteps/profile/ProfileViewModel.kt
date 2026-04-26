package com.safesteps.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safesteps.auth.UserInfo
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var currentUser: UserInfo? = null
    private var loadJob: Job? = null
    private var saveJob: Job? = null
    private var latestSaveVersion = 0L

    fun onCurrentUserChanged(user: UserInfo?) {
        val googleId = user?.googleId?.takeIf { it.isNotBlank() }
        val currentGoogleId = currentUser?.googleId?.takeIf { it.isNotBlank() }
        if (googleId == currentGoogleId) {
            return
        }

        currentUser = user
        loadJob?.cancel()
        saveJob?.cancel()
        latestSaveVersion += 1

        if (googleId == null) {
            _uiState.value = ProfileUiState()
            return
        }

        _uiState.value = ProfileUiState(isLoadingFilters = true)

        loadJob = viewModelScope.launch {
            try {
                val loadedFilters = withContext(ioDispatcher) {
                    cargarFiltrosUsuario(googleId)
                }

                if (currentUser?.googleId != googleId) {
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
                if (currentUser?.googleId != googleId) {
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
        latestSaveVersion += 1
        val saveVersion = latestSaveVersion

        saveJob = viewModelScope.launch {
            try {
                val persistedFilters = withContext(ioDispatcher) {
                    actualizarFiltrosUsuario(googleId, updatedFilters.toUserFilters())
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
            }
        }
    }
}
