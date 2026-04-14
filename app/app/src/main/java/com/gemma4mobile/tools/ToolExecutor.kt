package com.gemma4mobile.tools

interface ToolExecutor {
    val toolName: ToolName
    suspend fun execute(arguments: Map<String, Any>): ToolResult
}
