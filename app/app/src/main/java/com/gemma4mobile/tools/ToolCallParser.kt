package com.gemma4mobile.tools

import android.util.Log
import org.json.JSONObject

object ToolCallParser {

    private const val TAG = "ToolCallParser"
    private val TOOL_CALL_REGEX = Regex(
        """<tool_call>\s*(\{.*?\})\s*</tool_call>""",
        RegexOption.DOT_MATCHES_ALL
    )

    fun parse(modelOutput: String): List<ToolCall> {
        return TOOL_CALL_REGEX.findAll(modelOutput).mapNotNull { match ->
            try {
                val json = JSONObject(match.groupValues[1])
                val name = json.getString("name")
                val args = json.optJSONObject("arguments") ?: JSONObject()
                val argsMap = mutableMapOf<String, Any>()
                args.keys().forEach { key ->
                    argsMap[key] = args.get(key)
                }
                ToolCall(name = name, arguments = argsMap)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse tool_call: ${e.message}")
                null
            }
        }.toList()
    }

    fun extractText(modelOutput: String): String {
        return modelOutput.replace(TOOL_CALL_REGEX, "").trim()
    }

    fun hasOpenToolCall(partialOutput: String): Boolean {
        val opens = partialOutput.split("<tool_call>").size - 1
        val closes = partialOutput.split("</tool_call>").size - 1
        return opens > closes
    }
}
