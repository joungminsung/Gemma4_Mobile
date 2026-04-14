package com.gemma4mobile.tools

import org.json.JSONObject

data class ToolCall(
    val name: String,
    val arguments: Map<String, Any>,
)

data class ToolResult(
    val name: String,
    val result: JSONObject? = null,
    val error: String? = null,
) {
    fun toXml(): String {
        val json = JSONObject()
        json.put("name", name)
        if (error != null) {
            json.put("error", error)
        } else {
            json.put("result", result ?: JSONObject())
        }
        return "<tool_result>\n$json\n</tool_result>"
    }
}

enum class ToolName(val displayName: String, val statusMessage: String) {
    SEARCH_WEB("search_web", "검색 중..."),
    READ_CALENDAR("read_calendar", "캘린더 조회 중..."),
    WRITE_CALENDAR("write_calendar", "캘린더에 추가 중..."),
    READ_SMS("read_sms", "메시지 읽는 중..."),
    SEND_SMS("send_sms", "메시지 보내는 중..."),
    READ_CALL_LOG("read_call_log", "통화 기록 조회 중..."),
    MAKE_CALL("make_call", "전화 거는 중..."),
    READ_CONTACTS("read_contacts", "연락처 검색 중..."),
    WRITE_CONTACT("write_contact", "연락처 추가 중..."),
    READ_ALARMS("read_alarms", "알람 조회 중..."),
    SET_ALARM("set_alarm", "알람 설정 중...");

    val requiresConfirmation: Boolean
        get() = this == SEND_SMS || this == MAKE_CALL

    companion object {
        fun fromString(name: String): ToolName? =
            entries.find { it.displayName == name }
    }
}
