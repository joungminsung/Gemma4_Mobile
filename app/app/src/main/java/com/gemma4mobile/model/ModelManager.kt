package com.gemma4mobile.model

import android.content.Context
import com.gemma4mobile.inference.GemmaInferenceEngine
import com.gemma4mobile.inference.InferenceState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    fun loadModel(tier: ModelTier) {
        engine.unload()
        val path = downloader.getModelPath(tier)
        engine.loadModel(path, context)
        _currentTier.value = tier
    }

    fun unloadModel() {
        engine.unload()
        _currentTier.value = null
    }

    val inferenceState: InferenceState
        get() = engine.state
}
