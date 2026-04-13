package com.gemma4mobile.model

enum class ModelTier(
    val modelFilename: String,
    val downloadSizeMb: Long,
    val displayName: String,
    val minRamMb: Int,
) {
    LITE(
        modelFilename = "gemma4_ko_lite.task",
        downloadSizeMb = 300,
        displayName = "Lite",
        minRamMb = 4000,
    ),
    STANDARD(
        modelFilename = "gemma4_ko_standard.task",
        downloadSizeMb = 1200,
        displayName = "Standard",
        minRamMb = 6000,
    ),
    FULL(
        modelFilename = "gemma4_ko_full.task",
        downloadSizeMb = 2000,
        displayName = "Full",
        minRamMb = 8000,
    ),
    MAX(
        modelFilename = "gemma4_ko_max.task",
        downloadSizeMb = 4000,
        displayName = "Max",
        minRamMb = 12000,
    );

    companion object {
        fun forDevice(ramMb: Int): ModelTier? {
            return entries
                .sortedByDescending { it.minRamMb }
                .firstOrNull { ramMb >= it.minRamMb }
        }
    }
}
