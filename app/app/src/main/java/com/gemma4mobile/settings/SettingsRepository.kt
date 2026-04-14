package com.gemma4mobile.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { DARK, LIGHT, SYSTEM }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val systemPrompt: String = "",
    val temperature: Float = 0.8f,
    val topP: Float = 0.95f,
    val maxTokens: Int = 1024,
    val sttLanguage: String = "ko-KR",
    val autoSendVoice: Boolean = false,
    val assistButtonEnabled: Boolean = false,
)

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val TOP_P = floatPreferencesKey("top_p")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val STT_LANGUAGE = stringPreferencesKey("stt_language")
        val AUTO_SEND_VOICE = booleanPreferencesKey("auto_send_voice")
        val ASSIST_BUTTON = booleanPreferencesKey("assist_button")
    }

    val settingsFlow: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.DARK,
            systemPrompt = prefs[Keys.SYSTEM_PROMPT] ?: "",
            temperature = prefs[Keys.TEMPERATURE] ?: 0.8f,
            topP = prefs[Keys.TOP_P] ?: 0.95f,
            maxTokens = prefs[Keys.MAX_TOKENS] ?: 1024,
            sttLanguage = prefs[Keys.STT_LANGUAGE] ?: "ko-KR",
            autoSendVoice = prefs[Keys.AUTO_SEND_VOICE] ?: false,
            assistButtonEnabled = prefs[Keys.ASSIST_BUTTON] ?: false,
        )
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun updateSystemPrompt(prompt: String) {
        dataStore.edit { it[Keys.SYSTEM_PROMPT] = prompt }
    }

    suspend fun updateTemperature(value: Float) {
        dataStore.edit { it[Keys.TEMPERATURE] = value }
    }

    suspend fun updateTopP(value: Float) {
        dataStore.edit { it[Keys.TOP_P] = value }
    }

    suspend fun updateMaxTokens(value: Int) {
        dataStore.edit { it[Keys.MAX_TOKENS] = value }
    }

    suspend fun updateSttLanguage(language: String) {
        dataStore.edit { it[Keys.STT_LANGUAGE] = language }
    }

    suspend fun updateAutoSendVoice(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_SEND_VOICE] = enabled }
    }

    suspend fun updateAssistButton(enabled: Boolean) {
        dataStore.edit { it[Keys.ASSIST_BUTTON] = enabled }
    }
}
