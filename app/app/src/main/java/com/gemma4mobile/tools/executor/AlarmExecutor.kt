package com.gemma4mobile.tools.executor

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.gemma4mobile.tools.ToolExecutor
import com.gemma4mobile.tools.ToolName
import com.gemma4mobile.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmReadExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ToolExecutor {

    override val toolName = ToolName.READ_ALARMS

    override suspend fun execute(arguments: Map<String, Any>): ToolResult {
        val result = JSONObject()
        result.put("message", "알람 목록은 직접 조회가 제한됩니다. 알람 앱을 확인하거나, 새 알람을 설정해 드릴 수 있습니다.")
        return ToolResult(name = toolName.displayName, result = result)
    }
}

@Singleton
class AlarmSetExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ToolExecutor {

    override val toolName = ToolName.SET_ALARM

    override suspend fun execute(arguments: Map<String, Any>): ToolResult {
        val hour = (arguments["hour"] as? Number)?.toInt()
            ?: return ToolResult(name = toolName.displayName, error = "hour is required")
        val minute = (arguments["minute"] as? Number)?.toInt()
            ?: return ToolResult(name = toolName.displayName, error = "minute is required")
        val label = arguments["label"]?.toString()

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)

            val result = JSONObject()
            result.put("status", "set")
            result.put("hour", hour)
            result.put("minute", minute)
            label?.let { result.put("label", it) }
            ToolResult(name = toolName.displayName, result = result)
        } catch (e: Exception) {
            ToolResult(name = toolName.displayName, error = "알람 설정 실패: ${e.message}")
        }
    }
}
