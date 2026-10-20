package com.salihraphics.meow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salihraphics.meow.data.preferences.UserPreferencesRepository
import com.salihraphics.meow.domain.model.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface MainUiState {
    /** Preferences are being read; the system splash stays up meanwhile (typically a few ms). */
    data object Loading : MainUiState
    data class Ready(val preferences: UserPreferences) : MainUiState
}

class MainViewModel(repository: UserPreferencesRepository) : ViewModel() {
    val uiState: StateFlow<MainUiState> = repository.preferences
        .map<UserPreferences, MainUiState> { MainUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)
}
