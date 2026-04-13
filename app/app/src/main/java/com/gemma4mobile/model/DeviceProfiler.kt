package com.gemma4mobile.model

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs

class DeviceProfiler(
    private val totalRamMb: Int,
    private val availableStorageMb: Long = Long.MAX_VALUE,
) {
    val recommendedTier: ModelTier?
        get() = ModelTier.forDevice(totalRamMb)

    val availableTiers: List<ModelTier>
        get() = ModelTier.entries.filter { totalRamMb >= it.minRamMb }

    fun hasEnoughStorage(tier: ModelTier): Boolean {
        return availableStorageMb >= tier.downloadSizeMb
    }

    companion object {
        fun fromContext(context: Context): DeviceProfiler {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            val totalRamMb = (memInfo.totalMem / (1024 * 1024)).toInt()

            val stat = StatFs(Environment.getDataDirectory().path)
            val availableStorageMb = stat.availableBytes / (1024 * 1024)

            return DeviceProfiler(totalRamMb, availableStorageMb)
        }
    }
}
