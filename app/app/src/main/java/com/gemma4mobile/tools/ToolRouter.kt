package com.gemma4mobile.tools

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

data class ToolStatus(
    val toolName: ToolName? = null,
    val isExecuting: Boolean = false,
)

@Singleton
class ToolRouter @Inject constructor() {

    private val executors = mutableMapOf<String, ToolExecutor>()

    fun register(executor: ToolExecutor) {
        executors[executor.toolName.displayName] = executor
    }

    suspend fun execute(toolCall: ToolCall): ToolResult {
        val executor = executors[toolCall.name]
            ?: return ToolResult(
                name = toolCall.name,
                error = "Unknown tool: ${toolCall.name}"
            )
        return try {
            executor.execute(toolCall.arguments)
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution failed: ${toolCall.name}", e)
            ToolResult(
                name = toolCall.name,
                error = "실행 실패: ${e.message}"
            )
        }
    }

    fun getToolName(name: String): ToolName? = ToolName.fromString(name)

    companion object {
        private const val TAG = "ToolRouter"
        const val MAX_CHAIN_COUNT = 5
    }
}
