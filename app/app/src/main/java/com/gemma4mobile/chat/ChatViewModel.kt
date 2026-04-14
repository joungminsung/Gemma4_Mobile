package com.gemma4mobile.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gemma4mobile.db.ChatMessage
import com.gemma4mobile.db.ChatSession
import com.gemma4mobile.inference.InferenceState
import com.gemma4mobile.inference.Turn
import com.gemma4mobile.model.ModelManager
import com.gemma4mobile.tools.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ToolCallUiInfo(
    val toolName: ToolName,
    val arguments: Map<String, Any>,
)

data class ThinkingStep(
    val label: String,
    val isDone: Boolean = false,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val modelTier: String = "",
    val error: String? = null,
    val toolStatus: ToolStatus = ToolStatus(),
    val pendingConfirmation: ToolCallUiInfo? = null,
    val thinkingSteps: List<ThinkingStep> = emptyList(),
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val modelManager: ModelManager,
    private val toolRouter: ToolRouter,
    private val permissionManager: ToolPermissionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    private val _sessions = MutableStateFlow<Map<String, List<ChatSession>>>(emptyMap())
    val sessions: StateFlow<Map<String, List<ChatSession>>> = _sessions.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _permissionRequest = MutableSharedFlow<List<String>>()
    val permissionRequest: SharedFlow<List<String>> = _permissionRequest.asSharedFlow()

    private var messagesJob: Job? = null
    private var pendingToolCall: ToolCall? = null

    init {
        viewModelScope.launch {
            val session = repository.createSession()
            _currentSessionId.value = session.id
            _uiState.update {
                it.copy(modelTier = modelManager.currentTier.value?.displayName ?: "")
            }
            observeMessages(session.id)
        }
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

    private fun addThinkingStep(label: String) {
        _uiState.update { state ->
            state.copy(thinkingSteps = state.thinkingSteps + ThinkingStep(label))
        }
    }

    private fun completeLastThinkingStep() {
        _uiState.update { state ->
            val steps = state.thinkingSteps.toMutableList()
            if (steps.isNotEmpty()) {
                steps[steps.lastIndex] = steps.last().copy(isDone = true)
            }
            state.copy(thinkingSteps = steps)
        }
    }

    fun sendMessage(text: String) {
        val sessionId = _currentSessionId.value ?: return
        if (modelManager.inferenceState != InferenceState.READY) return

        viewModelScope.launch {
            repository.addMessage(sessionId, "user", text)

            val systemPrompt = SystemPromptBuilder.build("")
            val history = _uiState.value.messages.map { Turn(it.role, it.content) }

            _uiState.update { it.copy(isGenerating = true, streamingText = "", thinkingSteps = emptyList()) }

            runInferenceWithToolLoop(sessionId, text, systemPrompt, history)
        }
    }

    private suspend fun runInferenceWithToolLoop(
        sessionId: String,
        initialPrompt: String,
        systemPrompt: String,
        initialHistory: List<Turn>,
    ) {
        val conversationContext = StringBuilder()
        conversationContext.append("$systemPrompt\n\n")
        for (turn in initialHistory) {
            conversationContext.append("${turn.role}: ${turn.content}\n")
        }
        conversationContext.append("user: $initialPrompt\n")

        var chainCount = 0

        try {
            while (chainCount <= ToolRouter.MAX_CHAIN_COUNT) {
                addThinkingStep("추론 중...")

                val fullResponse = StringBuilder()

                modelManager.engine.generateStream(conversationContext.toString()).collect { token ->
                    fullResponse.append(token)
                    // 항상 extractText로 tool_call JSON을 필터링
                    val displayText = ToolCallParser.extractText(fullResponse.toString())
                    _uiState.update { it.copy(streamingText = displayText) }
                }

                completeLastThinkingStep()

                val output = fullResponse.toString()
                val toolCalls = ToolCallParser.parse(output)

                if (toolCalls.isEmpty()) {
                    // 툴 호출 없음 — 최종 응답
                    val finalText = ToolCallParser.extractText(output)
                    repository.addMessage(sessionId, "model", finalText)
                    break
                }

                // 스트리밍 텍스트에서 tool_call 제거한 텍스트만 유지
                _uiState.update { it.copy(streamingText = ToolCallParser.extractText(output)) }

                val toolResultsText = StringBuilder()
                for (tc in toolCalls) {
                    val tn = toolRouter.getToolName(tc.name)

                    // 권한 확인
                    if (tn != null && !permissionManager.hasPermissions(tn)) {
                        val missing = permissionManager.getMissingPermissions(tn)
                        _permissionRequest.emit(missing)
                        addThinkingStep("${tn.statusMessage} (권한 필요)")
                        completeLastThinkingStep()
                        val errorResult = ToolResult(name = tc.name, error = "권한이 필요합니다: ${missing.joinToString()}")
                        toolResultsText.append(errorResult.toXml()).append("\n")
                        continue
                    }

                    // 위험 액션 확인
                    if (tn != null && tn.requiresConfirmation) {
                        addThinkingStep("${tn.statusMessage} — 확인 대기")
                        _uiState.update {
                            it.copy(pendingConfirmation = ToolCallUiInfo(tn, tc.arguments))
                        }
                        pendingToolCall = tc
                        return
                    }

                    // 툴 실행
                    addThinkingStep(tn?.statusMessage ?: "${tc.name} 실행 중...")
                    _uiState.update { it.copy(toolStatus = ToolStatus(tn, true)) }
                    val result = toolRouter.execute(tc)
                    _uiState.update { it.copy(toolStatus = ToolStatus()) }
                    completeLastThinkingStep()
                    toolResultsText.append(result.toXml()).append("\n")
                }

                conversationContext.append("model: $output\n")
                conversationContext.append(toolResultsText.toString())
                chainCount++
            }

            if (chainCount > ToolRouter.MAX_CHAIN_COUNT) {
                repository.addMessage(sessionId, "model", "요청을 처리하는 데 너무 많은 단계가 필요합니다. 질문을 더 간단하게 해주세요.")
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = e.message) }
        } finally {
            _uiState.update {
                it.copy(isGenerating = false, streamingText = "", toolStatus = ToolStatus(), thinkingSteps = emptyList())
            }
        }
    }

    fun confirmToolExecution() {
        val tc = pendingToolCall ?: return
        val sessionId = _currentSessionId.value ?: return
        pendingToolCall = null
        _uiState.update { it.copy(pendingConfirmation = null) }

        viewModelScope.launch {
            val tn = toolRouter.getToolName(tc.name)
            completeLastThinkingStep()
            addThinkingStep(tn?.statusMessage ?: "${tc.name} 실행 중...")
            _uiState.update { it.copy(toolStatus = ToolStatus(tn, true)) }
            val result = toolRouter.execute(tc)
            _uiState.update { it.copy(toolStatus = ToolStatus()) }
            completeLastThinkingStep()

            val resultText = result.result?.toString(2) ?: result.error ?: ""
            repository.addMessage(sessionId, "model", "${tn?.statusMessage ?: tc.name} 완료\n$resultText")
            _uiState.update { it.copy(isGenerating = false, streamingText = "", thinkingSteps = emptyList()) }
        }
    }

    fun denyToolExecution() {
        pendingToolCall = null
        val sessionId = _currentSessionId.value ?: return

        viewModelScope.launch {
            repository.addMessage(sessionId, "model", "실행이 취소되었습니다.")
            _uiState.update {
                it.copy(
                    pendingConfirmation = null,
                    isGenerating = false,
                    streamingText = "",
                    thinkingSteps = emptyList(),
                )
            }
        }
    }

    fun onPermissionsResult(granted: Boolean) {
        if (!granted) {
            val sessionId = _currentSessionId.value ?: return
            viewModelScope.launch {
                repository.addMessage(sessionId, "model", "필요한 권한이 거부되어 기능을 실행할 수 없습니다.")
                _uiState.update { it.copy(isGenerating = false, streamingText = "", thinkingSteps = emptyList()) }
            }
        }
    }
}
