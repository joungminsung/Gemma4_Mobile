package com.gemma4mobile.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

sealed class DownloadState {
    data class Progress(val percent: Int) : DownloadState()
    data class Complete(val path: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class ModelDownloader(
    private val baseDir: String,
    private val baseUrl: String = "https://storage.googleapis.com/gemma4mobile/models",
) {
    private val client = OkHttpClient()

    fun modelExists(tier: ModelTier): Boolean {
        return File(getModelPath(tier)).exists()
    }

    fun getModelPath(tier: ModelTier): String {
        return "$baseDir/${tier.modelFilename}"
    }

    fun downloadUrl(tier: ModelTier): String {
        return "$baseUrl/${tier.modelFilename}"
    }

    fun download(tier: ModelTier): Flow<DownloadState> = flow {
        val url = downloadUrl(tier)
        val destFile = File(getModelPath(tier))
        destFile.parentFile?.mkdirs()

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            emit(DownloadState.Error("Download failed: ${response.code}"))
            return@flow
        }

        val body = response.body ?: run {
            emit(DownloadState.Error("Empty response body"))
            return@flow
        }

        val totalBytes = body.contentLength()
        var downloadedBytes = 0L

        body.byteStream().use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    if (totalBytes > 0) {
                        val percent = (downloadedBytes * 100 / totalBytes).toInt()
                        emit(DownloadState.Progress(percent))
                    }
                }
            }
        }

        emit(DownloadState.Complete(destFile.absolutePath))
    }.flowOn(Dispatchers.IO)
}
