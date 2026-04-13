package com.gemma4mobile.model

import android.content.Context
import android.util.Log
import com.gemma4mobile.inference.GemmaInferenceEngine
import com.gemma4mobile.inference.InferenceState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val profiler = DeviceProfiler.fromContext(context)
    private val downloader = ModelDownloader(
        baseDir = context.filesDir.resolve("models").absolutePath,
    )
    val engine = GemmaInferenceEngine()

    private val _currentTier = MutableStateFlow<ModelTier?>(null)
    val currentTier: StateFlow<ModelTier?> = _currentTier

    val recommendedTier: ModelTier?
        get() = profiler.recommendedTier

    val availableTiers: List<ModelTier>
        get() = profiler.availableTiers

    fun isModelDownloaded(tier: ModelTier): Boolean {
        return downloader.modelExists(tier)
    }

    fun downloadModel(tier: ModelTier): Flow<DownloadState> {
        return downloader.download(tier)
    }

    suspend fun loadModel(tier: ModelTier) {
        engine.unload()
        val path = downloader.getModelPath(tier)
        engine.loadModel(path, context)
        _currentTier.value = tier
    }

    /**
     * 개발자 모드: 외부 경로의 모델을 앱 내부 저장소로 복사 후 로드.
     * /data/local/tmp/ 등 외부 경로는 앱이 직접 접근 불가할 수 있으므로
     * 반드시 앱 내부로 복사한 뒤 로드한다.
     */
    suspend fun loadModelFromPath(sourcePath: String) {
        engine.unload()
        withContext(Dispatchers.IO) {
            val sourceFile = File(sourcePath)
            val internalDir = context.filesDir.resolve("models")
            internalDir.mkdirs()
            val destFile = File(internalDir, sourceFile.name)

            if (!destFile.exists()) {
                Log.d(TAG, "Copying model to internal storage: ${destFile.absolutePath}")
                sourceFile.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 8192)
                    }
                }
                Log.d(TAG, "Copy complete: ${destFile.length()} bytes")
            } else {
                Log.d(TAG, "Model already in internal storage: ${destFile.absolutePath}")
            }

            engine.loadModel(destFile.absolutePath, context)
        }
        _currentTier.value = ModelTier.LITE
    }

    fun unloadModel() {
        engine.unload()
        _currentTier.value = null
    }

    val inferenceState: InferenceState
        get() = engine.state

    companion object {
        private const val TAG = "ModelManager"
        const val DEV_MODEL_PATH = "/data/local/tmp/gemma4mobile/gemma-4-E2B-it.litertlm"
    }
}
