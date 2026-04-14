package com.gemma4mobile.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gemma4mobile.db.ChatMessage
import com.gemma4mobile.db.ChatSession
import com.gemma4mobile.inference.InferenceState
import com.gemma4mobile.inference.Turn
import com.gemma4mobile.model.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    private val _sessions = MutableStateFlow<Map<String, List<ChatSession>>>(emptyMap())
    val sessions: StateFlow<Map<String, List<ChatSession>>> = _sessions.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var messagesJob: Job? = null

    init {
        viewModelScope.launch {
            val session = repository.createSession()
            _currentSessionId.value = session.id
            _uiState.update {
                it.copy(modelTier = modelManager.currentTier.value?.displayName ?: "")
            }
            observeMessages(session.id)
        }
        // Observe sessions
        viewModelScope.launch {
            _searchQuery.collectLatest { query ->
                val sessionsFlow = if (query.isBlank()) {
                    repository.getSessions()
                } else {
                    repository.searchSessions(query)
                }
                sessionsFlow.collect { sessionList ->
                    _sessions.value = repository.groupSessionsByDate(sessionList)
                }
            }
        }
    }

    private fun observeMessages(sessionId: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repository.getMessages(sessionId).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun switchSession(sessionId: String) {
        _currentSessionId.value = sessionId
        _uiState.update { it.copy(messages = emptyList(), streamingText = "", isGenerating = false) }
        observeMessages(sessionId)
    }

    fun createNewSession() {
        viewModelScope.launch {
            val session = repository.createSession()
            _currentSessionId.value = session.id
            _uiState.update { it.copy(messages = emptyList(), streamingText = "", isGenerating = false) }
            observeMessages(session.id)
        }
    }

    fun renameSession(sessionId: String, title: String) {
        viewModelScope.launch {
            repository.renameSession(sessionId, title)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(ChatSession(id = sessionId, title = ""))
            if (_currentSessionId.value == sessionId) {
                createNewSession()
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun sendMessage(text: String) {
        val sessionId = _currentSessionId.value ?: return
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
