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
    private var currentModelPath: String? = null
    private var currentContext: android.content.Context? = null

    var state: InferenceState = InferenceState.UNLOADED
        private set

    fun formatPrompt(message: String, history: List<Turn> = emptyList()): String {
        return message
    }

    suspend fun loadModel(modelPath: String, context: android.content.Context) {
        state = InferenceState.LOADING
        currentModelPath = modelPath
        currentContext = context
        withContext(Dispatchers.IO) {
            initEngine(modelPath, context, Backend.GPU())
        }
    }

    private fun initEngine(modelPath: String, context: android.content.Context, backend: Backend) {
        try {
            Log.d(TAG, "Initializing engine with backend: ${backend::class.simpleName}")
            engine?.close()
            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = backend,
                cacheDir = context.cacheDir.absolutePath,
            )
            engine = Engine(engineConfig)
            engine!!.initialize()
            conversation = engine!!.createConversation()
            state = InferenceState.READY
            Log.d(TAG, "Engine ready with ${backend::class.simpleName}")
        } catch (e: Exception) {
            Log.e(TAG, "initEngine failed: ${e.message}", e)
            state = InferenceState.ERROR
            throw e
        }
    }

    fun generateStream(prompt: String): Flow<String> = flow {
        state = InferenceState.GENERATING
        val conv = conversation ?: throw IllegalStateException("Model not loaded")

        try {
            val message = Message.of(prompt)
            conv.sendMessageAsync(message)
                .collect { chunk ->
                    emit(chunk.toString())
                }
            state = InferenceState.READY
        } catch (e: Exception) {
            Log.w(TAG, "Inference failed: ${e.message}")

            // GPU 실패 시 CPU로 폴백 재초기화
            val path = currentModelPath
            val ctx = currentContext
            if (path != null && ctx != null) {
                Log.d(TAG, "Falling back to CPU backend...")
                try {
                    initEngine(path, ctx, Backend.CPU())
                    // CPU로 재시도
                    val retryConv = conversation ?: throw IllegalStateException("CPU fallback failed")
                    val retryMsg = Message.of(prompt)
                    retryConv.sendMessageAsync(retryMsg)
                        .collect { chunk ->
                            emit(chunk.toString())
                        }
                    state = InferenceState.READY
                    return@flow
                } catch (cpuError: Exception) {
                    Log.e(TAG, "CPU fallback also failed: ${cpuError.message}")
                    state = InferenceState.ERROR
                    throw cpuError
                }
            }

            state = InferenceState.ERROR
            throw e
        }
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
        currentModelPath = null
        currentContext = null
        state = InferenceState.UNLOADED
    }

    companion object {
        private const val TAG = "GemmaInference"
    }
}
