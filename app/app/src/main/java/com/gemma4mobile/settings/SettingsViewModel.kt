package com.gemma4mobile.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun updateThemeMode(mode: ThemeMode) = viewModelScope.launch { repository.updateThemeMode(mode) }
    fun updateSystemPrompt(prompt: String) = viewModelScope.launch { repository.updateSystemPrompt(prompt) }
    fun updateTemperature(value: Float) = viewModelScope.launch { repository.updateTemperature(value) }
    fun updateTopP(value: Float) = viewModelScope.launch { repository.updateTopP(value) }
    fun updateMaxTokens(value: Int) = viewModelScope.launch { repository.updateMaxTokens(value) }
    fun updateSttLanguage(lang: String) = viewModelScope.launch { repository.updateSttLanguage(lang) }
    fun updateAutoSendVoice(enabled: Boolean) = viewModelScope.launch { repository.updateAutoSendVoice(enabled) }
    fun updateAssistButton(enabled: Boolean) = viewModelScope.launch { repository.updateAssistButton(enabled) }
}
