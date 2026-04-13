package com.gemma4mobile.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gemma4mobile.db.ChatMessage
import com.gemma4mobile.inference.InferenceState
import com.gemma4mobile.inference.Turn
import com.gemma4mobile.model.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val modelTier: String = "",
    val error: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val modelManager: ModelManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentSessionId: String? = null

    init {
        viewModelScope.launch {
            val session = repository.createSession()
            currentSessionId = session.id
            _uiState.update {
                it.copy(modelTier = modelManager.currentTier.value?.displayName ?: "")
            }

            repository.getMessages(session.id).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun sendMessage(text: String) {
        val sessionId = currentSessionId ?: return
        if (modelManager.inferenceState != InferenceState.READY) return

        viewModelScope.launch {
            repository.addMessage(sessionId, "user", text)

            val history = _uiState.value.messages.map { Turn(it.role, it.content) }
            val prompt = modelManager.engine.formatPrompt(text, history)

            _uiState.update { it.copy(isGenerating = true, streamingText = "") }

            val fullResponse = StringBuilder()
            try {
                modelManager.engine.generateStream(prompt).collect { token ->
                    fullResponse.append(token)
                    _uiState.update { it.copy(streamingText = fullResponse.toString()) }
                }
                repository.addMessage(sessionId, "model", fullResponse.toString())
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isGenerating = false, streamingText = "") }
            }
        }
    }
}
