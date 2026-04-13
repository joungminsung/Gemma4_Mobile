package com.gemma4mobile.model

import org.junit.Assert.*
import org.junit.Test

class ModelDownloaderTest {

    @Test
    fun `modelExists returns false when file does not exist`() {
        val downloader = ModelDownloader(baseDir = "/tmp/nonexistent_gemma4_test")
        assertFalse(downloader.modelExists(ModelTier.LITE))
    }

    @Test
    fun `getModelPath returns correct path`() {
        val downloader = ModelDownloader(baseDir = "/data/models")
        assertEquals(
            "/data/models/gemma4_ko_lite.task",
            downloader.getModelPath(ModelTier.LITE),
        )
    }

    @Test
    fun `downloadUrl returns correct URL for tier`() {
        val downloader = ModelDownloader(
            baseDir = "/data/models",
            baseUrl = "https://storage.example.com/models",
        )
        assertEquals(
            "https://storage.example.com/models/gemma4_ko_full.task",
            downloader.downloadUrl(ModelTier.FULL),
        )
    }
}
