package com.gemma4mobile.inference

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

data class Turn(val role: String, val content: String)

enum class InferenceState {
    UNLOADED, LOADING, READY, GENERATING, ERROR
}

class GemmaInferenceEngine {

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    var state: InferenceState = InferenceState.UNLOADED
        private set

    fun formatPrompt(message: String, history: List<Turn> = emptyList()): String {
        return message
    }

    suspend fun loadModel(modelPath: String, context: android.content.Context) {
        state = InferenceState.LOADING
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Loading model with GPU: $modelPath")
                val engineConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.GPU(),
                    cacheDir = context.cacheDir.absolutePath,
                )
                engine = Engine(engineConfig)
                engine!!.initialize()
                conversation = engine!!.createConversation()
                state = InferenceState.READY
                Log.d(TAG, "Model loaded successfully with GPU")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model: ${e.message}", e)
                state = InferenceState.ERROR
                throw e
            }
        }
    }

    fun generateStream(prompt: String): Flow<String> = flow {
        state = InferenceState.GENERATING
        val conv = conversation ?: throw IllegalStateException("Model not loaded")

        val message = Message.of(prompt)
        conv.sendMessageAsync(message)
            .collect { chunk ->
                emit(chunk.toString())
            }

        state = InferenceState.READY
    }.flowOn(Dispatchers.IO)

    fun resetConversation() {
        conversation?.close()
        conversation = engine?.createConversation()
    }

    fun unload() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
        state = InferenceState.UNLOADED
    }

    companion object {
        private const val TAG = "GemmaInference"
    }
}
