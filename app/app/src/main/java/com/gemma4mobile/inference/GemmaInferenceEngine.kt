package com.gemma4mobile.inference

import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

data class Turn(val role: String, val content: String)

enum class InferenceState {
    UNLOADED, LOADING, READY, GENERATING, ERROR
}

class GemmaInferenceEngine {

    private var llmInference: LlmInference? = null

    var state: InferenceState = InferenceState.UNLOADED
        private set

    fun formatPrompt(message: String, history: List<Turn> = emptyList()): String {
        val sb = StringBuilder()
        for (turn in history) {
            sb.append("<start_of_turn>${turn.role}\n${turn.content}<end_of_turn>\n")
        }
        sb.append("<start_of_turn>user\n$message<end_of_turn>\n")
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    fun loadModel(modelPath: String, context: android.content.Context) {
        state = InferenceState.LOADING
        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
            state = InferenceState.READY
        } catch (e: Exception) {
            state = InferenceState.ERROR
            throw e
        }
    }

    fun generateStream(prompt: String): Flow<String> = callbackFlow {
        state = InferenceState.GENERATING

        llmInference?.generateResponseAsync(prompt) { partialResult, done ->
            if (partialResult != null) {
                trySend(partialResult)
            }
            if (done) {
                state = InferenceState.READY
                close()
            }
        } ?: run {
            state = InferenceState.ERROR
            close(IllegalStateException("Model not loaded"))
        }

        awaitClose {
            state = InferenceState.READY
        }
    }

    fun unload() {
        llmInference?.close()
        llmInference = null
        state = InferenceState.UNLOADED
    }
}
