# Tool System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Gemma4 Mobile 앱에 모델 자율 판단 기반 툴 시스템을 추가하여 인터넷 검색, 캘린더, SMS, 전화, 연락처, 알람 기능을 수행할 수 있게 한다.

**Architecture:** 모델 출력에서 `<tool_call>` JSON을 파싱하여 해당 ToolExecutor를 라우팅하고, 결과를 `<tool_result>`로 모델에 재주입하는 루프 구조. 최대 5회 체이닝. SMS 발송과 전화 걸기는 사용자 확인 게이트를 거침. UI는 리치 카드 + 상태 인디케이터.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt DI, OkHttp (DuckDuckGo 스크래핑), Android ContentProvider API (캘린더/SMS/통화/연락처), AlarmClock Intent

---

## File Structure

### New Files

```
app/app/src/main/java/com/gemma4mobile/tools/
├── ToolDefinition.kt             # 툴 스키마 데이터 클래스, JSON 직렬화
├── ToolCallParser.kt             # 모델 출력에서 <tool_call> 파싱
├── ToolRouter.kt                 # 툴 이름 → Executor 매핑 + 체이닝 루프
├── ToolExecutor.kt               # Executor 인터페이스 + ToolResult
├── ToolPermissionManager.kt      # 런타임 권한 요청 관리
├── SystemPromptBuilder.kt        # 툴 스키마를 시스템 프롬프트에 주입
├── executor/
│   ├── WebSearchExecutor.kt      # DuckDuckGo HTML 스크래핑
│   ├── CalendarExecutor.kt       # 캘린더 읽기/쓰기
│   ├── SmsExecutor.kt            # SMS 읽기/보내기
│   ├── CallLogExecutor.kt        # 통화 기록 조회 / 전화 걸기
│   ├── ContactsExecutor.kt       # 연락처 읽기/쓰기
│   └── AlarmExecutor.kt          # 알람 조회/설정
└── ui/
    ├── ToolStatusIndicator.kt    # "검색 중..." 상태 컴포넌트
    ├── ToolResultCards.kt        # 검색/캘린더/연락처/통화/SMS 리치 카드 (한 파일)
    └── ConfirmationSheet.kt      # SMS 발송/전화 걸기 확인 바텀시트
```

### Modified Files

```
app/app/src/main/AndroidManifest.xml          # 권한 추가
app/app/src/main/java/com/gemma4mobile/
├── chat/ChatViewModel.kt                      # 툴 체이닝 루프 통합
├── chat/ChatUiState → ChatViewModel.kt        # ToolStatus 상태 추가
├── di/AppModule.kt                            # ToolRouter, OkHttpClient provide
├── ui/chat/ChatScreen.kt                      # 리치 카드 렌더링, 상태 표시
├── ui/chat/AiResponseCard.kt                  # 툴 결과 카드 포함 렌더링
└── settings/SystemPromptPresets.kt            # 툴 지원 프리셋 추가
```

---

### Task 1: 툴 데이터 모델 + 파서

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/tools/ToolDefinition.kt`
- Create: `app/app/src/main/java/com/gemma4mobile/tools/ToolCallParser.kt`
- Create: `app/app/src/main/java/com/gemma4mobile/tools/ToolExecutor.kt`

- [ ] **Step 1: ToolDefinition.kt 생성 — 툴 호출/결과 데이터 클래스**

```kotlin
// app/app/src/main/java/com/gemma4mobile/tools/ToolDefinition.kt
package com.gemma4mobile.tools

import org.json.JSONArray
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
```

- [ ] **Step 2: ToolExecutor.kt 생성 — 실행 인터페이스**

```kotlin
// app/app/src/main/java/com/gemma4mobile/tools/ToolExecutor.kt
package com.gemma4mobile.tools

interface ToolExecutor {
    val toolName: ToolName
    suspend fun execute(arguments: Map<String, Any>): ToolResult
}
```

- [ ] **Step 3: ToolCallParser.kt 생성 — `<tool_call>` 파싱**

```kotlin
// app/app/src/main/java/com/gemma4mobile/tools/ToolCallParser.kt
package com.gemma4mobile.tools

import android.util.Log
import org.json.JSONObject

object ToolCallParser {

    private const val TAG = "ToolCallParser"
    private val TOOL_CALL_REGEX = Regex(
        """<tool_call>\s*(\{.*?\})\s*</tool_call>""",
        RegexOption.DOT_MATCHES_ALL
    )

    /**
     * 모델 출력에서 모든 <tool_call> 블록을 파싱한다.
     * 텍스트와 tool_call이 섞여 있을 수 있으므로 전체 텍스트에서 추출.
     */
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

    /**
     * 모델 출력에서 <tool_call> 블록을 제거한 순수 텍스트를 반환.
     */
    fun extractText(modelOutput: String): String {
        return modelOutput.replace(TOOL_CALL_REGEX, "").trim()
    }

    /**
     * 스트리밍 중 <tool_call> 태그가 열렸지만 아직 닫히지 않았는지 확인.
     * true이면 아직 tool_call을 수집 중이므로 UI에 표시하지 않음.
     */
    fun hasOpenToolCall(partialOutput: String): Boolean {
        val opens = partialOutput.split("<tool_call>").size - 1
        val closes = partialOutput.split("</tool_call>").size - 1
        return opens > closes
    }
}
```

- [ ] **Step 4: 빌드 확인**

Run: `cd /Users/dgsw36/Desktop/01_프로젝트-개발/AI/Gemma4_Mobile/app && ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/tools/ToolDefinition.kt \
       app/app/src/main/java/com/gemma4mobile/tools/ToolCallParser.kt \
       app/app/src/main/java/com/gemma4mobile/tools/ToolExecutor.kt
git commit -m "feat(tools): add tool data models, parser, and executor interface"
```

---

### Task 2: 시스템 프롬프트 빌더

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/tools/SystemPromptBuilder.kt`

- [ ] **Step 1: SystemPromptBuilder.kt 생성**

```kotlin
// app/app/src/main/java/com/gemma4mobile/tools/SystemPromptBuilder.kt
package com.gemma4mobile.tools

object SystemPromptBuilder {

    private val TOOL_SCHEMA = """
You are a helpful assistant with access to the following tools.
When you need to use a tool, output a <tool_call> block with JSON.
You may chain multiple tool calls — after receiving a <tool_result>, decide if you need another tool or can respond.
Always respond in the user's language.

Available tools:
- search_web(query: string) — Search the web. Returns title, snippet, url.
- read_calendar(start_date: string, end_date?: string) — Read calendar events. Dates in YYYY-MM-DD format.
- write_calendar(title: string, start: string, end: string, location?: string, description?: string) — Create a calendar event. start/end in ISO datetime.
- read_sms(contact?: string, count?: int) — Read SMS messages. contact is name or number.
- send_sms(to: string, message: string) — Send an SMS. to is name or phone number.
- read_call_log(contact?: string, count?: int) — Read call history.
- make_call(number: string) — Make a phone call. number is name or phone number.
- read_contacts(query: string, count?: int) — Search contacts by name.
- write_contact(name: string, phone: string, email?: string) — Add a new contact.
- read_alarms() — List current alarms.
- set_alarm(hour: int, minute: int, label?: string) — Set an alarm.

Tool call format:
<tool_call>
{"name": "tool_name", "arguments": {"key": "value"}}
</tool_call>

Rules:
- Only use tools when the user's request requires them.
- For normal conversation, respond directly without tools.
- After receiving <tool_result>, summarize the result naturally for the user.
""".trimIndent()

    fun build(userSystemPrompt: String): String {
        return if (userSystemPrompt.isBlank()) {
            TOOL_SCHEMA
        } else {
            "$TOOL_SCHEMA\n\nAdditional instructions:\n$userSystemPrompt"
        }
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd /Users/dgsw36/Desktop/01_프로젝트-개발/AI/Gemma4_Mobile/app && ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/tools/SystemPromptBuilder.kt
git commit -m "feat(tools): add system prompt builder with tool schema"
```

---

### Task 3: ToolRouter + 체이닝 루프

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/tools/ToolRouter.kt`
- Modify: `app/app/src/main/java/com/gemma4mobile/di/AppModule.kt`

- [ ] **Step 1: ToolRouter.kt 생성**

```kotlin
// app/app/src/main/java/com/gemma4mobile/tools/ToolRouter.kt
package com.gemma4mobile.tools

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
```

- [ ] **Step 2: AppModule.kt에 ToolRouter와 OkHttpClient provide 추가**

`AppModule.kt`에 추가:

```kotlin
// 기존 import에 추가
import com.gemma4mobile.tools.ToolRouter
import okhttp3.OkHttpClient

// AppModule object 내부에 추가
@Provides
@Singleton
fun provideOkHttpClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
}

@Provides
@Singleton
fun provideToolRouter(): ToolRouter {
    return ToolRouter()
}
```

- [ ] **Step 3: 빌드 확인**

Run: `cd /Users/dgsw36/Desktop/01_프로젝트-개발/AI/Gemma4_Mobile/app && ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/tools/ToolRouter.kt \
       app/app/src/main/java/com/gemma4mobile/di/AppModule.kt
git commit -m "feat(tools): add ToolRouter with chaining support and DI wiring"
```

---

### Task 4: WebSearchExecutor (DuckDuckGo 스크래핑)

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/tools/executor/WebSearchExecutor.kt`

- [ ] **Step 1: WebSearchExecutor.kt 생성**

```kotlin
// app/app/src/main/java/com/gemma4mobile/tools/executor/WebSearchExecutor.kt
package com.gemma4mobile.tools.executor

import android.util.Log
import com.gemma4mobile.tools.ToolExecutor
import com.gemma4mobile.tools.ToolName
import com.gemma4mobile.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSearchExecutor @Inject constructor(
    private val httpClient: OkHttpClient,
) : ToolExecutor {

    override val toolName = ToolName.SEARCH_WEB

    override suspend fun execute(arguments: Map<String, Any>): ToolResult {
        val query = arguments["query"]?.toString()
            ?: return ToolResult(name = toolName.displayName, error = "query is required")

        return withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = "https://html.duckduckgo.com/html/?q=$encoded"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                    .build()

                val response = httpClient.newCall(request).execute()
                val html = response.body?.string() ?: ""

                val items = parseSearchResults(html)
                val result = JSONObject()
                result.put("query", query)
                result.put("items", items)
                result.put("count", items.length())

                ToolResult(name = toolName.displayName, result = result)
            } catch (e: Exception) {
                Log.e(TAG, "Search failed: ${e.message}", e)
                ToolResult(name = toolName.displayName, error = "검색 실패: ${e.message}")
            }
        }
    }

    private fun parseSearchResults(html: String): JSONArray {
        val items = JSONArray()
        // DuckDuckGo HTML results are in <div class="result"> blocks
        // Each has <a class="result__a"> for title/url and <a class="result__snippet"> for snippet
        val resultPattern = Regex(
            """<div[^>]*class="[^"]*result[^"]*"[^>]*>.*?</div>\s*</div>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val titlePattern = Regex("""<a[^>]*class="result__a"[^>]*href="([^"]*)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val snippetPattern = Regex("""<a[^>]*class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)

        var count = 0
        for (match in resultPattern.findAll(html)) {
            if (count >= 5) break
            val block = match.value

            val titleMatch = titlePattern.find(block) ?: continue
            val snippetMatch = snippetPattern.find(block)

            val rawUrl = titleMatch.groupValues[1]
            val url = decodeRedirectUrl(rawUrl)
            val title = stripHtml(titleMatch.groupValues[2])
            val snippet = snippetMatch?.let { stripHtml(it.groupValues[1]) } ?: ""

            if (title.isNotBlank() && url.isNotBlank()) {
                val item = JSONObject()
                item.put("title", title)
                item.put("snippet", snippet)
                item.put("url", url)
                items.put(item)
                count++
            }
        }
        return items
    }

    private fun decodeRedirectUrl(url: String): String {
        // DuckDuckGo wraps URLs in a redirect: //duckduckgo.com/l/?uddg=ENCODED_URL&...
        if (url.contains("uddg=")) {
            val encoded = url.substringAfter("uddg=").substringBefore("&")
            return java.net.URLDecoder.decode(encoded, "UTF-8")
        }
        return url
    }

    private fun stripHtml(html: String): String {
        return html.replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#x27;", "'")
            .trim()
    }

    companion object {
        private const val TAG = "WebSearchExecutor"
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd /Users/dgsw36/Desktop/01_프로젝트-개발/AI/Gemma4_Mobile/app && ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/tools/executor/WebSearchExecutor.kt
git commit -m "feat(tools): add DuckDuckGo web search executor"
```

---

### Task 5: CalendarExecutor

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/tools/executor/CalendarExecutor.kt`

- [ ] **Step 1: CalendarExecutor.kt 생성**

```kotlin
// app/app/src/main/java/com/gemma4mobile/tools/executor/CalendarExecutor.kt
package com.gemma4mobile.tools.executor

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.gemma4mobile.tools.ToolExecutor
import com.gemma4mobile.tools.ToolName
import com.gemma4mobile.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarReadExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ToolExecutor {

    override val toolName = ToolName.READ_CALENDAR

    override suspend fun execute(arguments: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val startDate = arguments["start_date"]?.toString()
                ?: return@withContext ToolResult(name = toolName.displayName, error = "start_date is required")
            val endDate = arguments["end_date"]?.toString()

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val startMillis = dateFormat.parse(startDate)?.time
                ?: return@withContext ToolResult(name = toolName.displayName, error = "Invalid start_date format")
            val endMillis = if (endDate != null) {
                dateFormat.parse(endDate)?.time ?: (startMillis + 24 * 60 * 60 * 1000)
            } else {
                startMillis + 24 * 60 * 60 * 1000
            }

            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DESCRIPTION,
            )
            val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
            val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())
            val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

            val events = JSONArray()
            val datetimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection, selection, selectionArgs, sortOrder
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val event = JSONObject()
                    event.put("title", cursor.getString(1) ?: "")
                    event.put("start", datetimeFormat.format(Date(cursor.getLong(2))))
                    event.put("end", cursor.getLong(3).let { if (it > 0) datetimeFormat.format(Date(it)) else "" })
                    event.put("location", cursor.getString(4) ?: "")
                    event.put("description", cursor.getString(5) ?: "")
                    events.put(event)
                }
            }

            val result = JSONObject()
            result.put("events", events)
            result.put("count", events.length())
            ToolResult(name = toolName.displayName, result = result)
        } catch (e: SecurityException) {
            ToolResult(name = toolName.displayName, error = "캘린더 접근 권한이 없습니다")
        } catch (e: Exception) {
            ToolResult(name = toolName.displayName, error = "캘린더 조회 실패: ${e.message}")
        }
    }
}

@Singleton
class CalendarWriteExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ToolExecutor {

    override val toolName = ToolName.WRITE_CALENDAR

    override suspend fun execute(arguments: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val title = arguments["title"]?.toString()
                ?: return@withContext ToolResult(name = toolName.displayName, error = "title is required")
            val start = arguments["start"]?.toString()
                ?: return@withContext ToolResult(name = toolName.displayName, error = "start is required")
            val end = arguments["end"]?.toString()
                ?: return@withContext ToolResult(name = toolName.displayName, error = "end is required")
            val location = arguments["location"]?.toString()
            val description = arguments["description"]?.toString()

            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
            val startMillis = dateFormat.parse(start)?.time
                ?: return@withContext ToolResult(name = toolName.displayName, error = "Invalid start format")
            val endMillis = dateFormat.parse(end)?.time
                ?: return@withContext ToolResult(name = toolName.displayName, error = "Invalid end format")

            // Get default calendar ID
            val calendarId = getDefaultCalendarId()
                ?: return@withContext ToolResult(name = toolName.displayName, error = "캘린더를 찾을 수 없습니다")

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
                description?.let { put(CalendarContract.Events.DESCRIPTION, it) }
            }

            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = uri?.lastPathSegment

            val result = JSONObject()
            result.put("event_id", eventId)
            result.put("status", "created")
            result.put("title", title)
            ToolResult(name = toolName.displayName, result = result)
        } catch (e: SecurityException) {
            ToolResult(name = toolName.displayName, error = "캘린더 쓰기 권한이 없습니다")
        } catch (e: Exception) {
            ToolResult(name = toolName.displayName, error = "일정 추가 실패: ${e.message}")
        }
    }

    private fun getDefaultCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.IS_PRIMARY} = 1"
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection, selection, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        // Fallback: get first writable calendar
        val fallbackSelection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection, fallbackSelection, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return null
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd /Users/dgsw36/Desktop/01_프로젝트-개발/AI/Gemma4_Mobile/app && ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/tools/executor/CalendarExecutor.kt
git commit -m "feat(tools): add calendar read/write executors"
```

---

### Task 6: SmsExecutor

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/tools/executor/SmsExecutor.kt`

- [ ] **Step 1: SmsExecutor.kt 생성**

```kotlin
// app/app/src/main/java/com/gemma4mobile/tools/executor/SmsExecutor.kt
package com.gemma4mobile.tools.executor

import android.content.Context
import android.provider.Telephony
import android.telephony.SmsManager
import com.gemma4mobile.tools.ToolExecutor
import com.gemma4mobile.tools.ToolName
import com.gemma4mobile.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsReadExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ToolExecutor {

    override val toolName = ToolName.READ_SMS

    override suspend fun execute(arguments: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val contact = arguments["contact"]?.toString()
            val count = (arguments["count"] as? Number)?.toInt() ?: 10

            val selection = if (contact != null) {
                "${Telephony.Sms.ADDRESS} LIKE ? OR ${Telephony.Sms.ADDRESS} IN " +
                    "(SELECT ${android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER} " +
                    "FROM ${android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI} " +
                    "WHERE ${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?)"
            } else null
            val selectionArgs = if (contact != null) arrayOf("%$contact%", "%$contact%") else null

            val messages = JSONArray()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                ),
                selection, selectionArgs,
                "${Telephony.Sms.DATE} DESC LIMIT $count"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val msg = JSONObject()
                    msg.put("address", cursor.getString(0) ?: "")
                    msg.put("body", cursor.getString(1) ?: "")
                    msg.put("date", dateFormat.format(Date(cursor.getLong(2))))
                    msg.put("type", if (cursor.getInt(3) == Telephony.Sms.MESSAGE_TYPE_SENT) "sent" else "received")
                    messages.put(msg)
                }
            }

            val result = JSONObject()
            result.put("messages", messages)
            result.put("count", messages.length())
            ToolResult(name = toolName.displayName, result = result)
        } catch (e: SecurityException) {
            ToolResult(name = toolName.displayName, error = "SMS 접근 권한이 없습니다")
        } catch (e: Exception) {
            ToolResult(name = toolName.displayName, error = "SMS 조회 실패: ${e.message}")
        }
    }
}

@Singleton
class SmsSendExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ToolExecutor {

    override val toolName = ToolName.SEND_SMS

    override suspend fun execute(arguments: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val to = arguments["to"]?.toString()
                ?: return@withContext ToolResult(name = toolName.displayName, error = "to is required")
            val message = arguments["message"]?.toString()
                ?: return@withContext ToolResult(name = toolName.displayName, error = "message is required")

            // Resolve contact name to number if needed
            val number = resolveContactNumber(to) ?: to

            @Suppress("DEPRECATION")
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(number, null, parts, null, null)

            val result = JSONObject()
            result.put("status", "sent")
            result.put("to_resolved", number)
            result.put("message_length", message.length)
            ToolResult(name = toolName.displayName, result = result)
        } catch (e: SecurityException) {
            ToolResult(name = toolName.displayName, error = "SMS 발송 권한이 없습니다")
        } catch (e: Exception) {
            ToolResult(name = toolName.displayName, error = "SMS 발송 실패: ${e.message}")
        }
    }

    private fun resolveContactNumber(nameOrNumber: String): String? {
        // If it looks like a phone number already, return as-is
        if (nameOrNumber.matches(Regex("[+\\d\\s\\-()]+"))) return null

        val projection = arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$nameOrNumber%")

        context.contentResolver.query(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection, selection, selectionArgs, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd /Users/dgsw36/Desktop/01_프로젝트-개발/AI/Gemma4_Mobile/app && ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/tools/executor/SmsExecutor.kt
git commit -m "feat(tools): add SMS read/send executors"
```

---

### Task 7: CallLogExecutor

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/tools/executor/CallLogExecutor.kt`

- [ ] **Step 1: CallLogExecutor.kt 생성**

```kotlin
// app/app/src/main/java/com/gemma4mobile/tools/executor/CallLogExecutor.kt
package com.gemma4mobile.tools.executor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import com.gemma4mobile.tools.ToolExecutor
import com.gemma4mobile.tools.ToolName
import com.gemma4mobile.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallLogReadExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ToolExecutor {

    override val toolName = ToolName.READ_CALL_LOG

    override suspend fun execute(arguments: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val contact = arguments["contact"]?.toString()
            val count = (arguments["count"] as? Number)?.toInt() ?: 10

            val selection = if (contact != null) {
                "${CallLog.Calls.CACHED_NAME} LIKE ? OR ${CallLog.Calls.NUMBER} LIKE ?"
            } else null
            val selectionArgs = if (contact != null) arrayOf("%$contact%", "%$contact%") else null

            val calls = JSONArray()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE,
                ),
                selection, selectionArgs,
                "${CallLog.Calls.DATE} DESC LIMIT $count"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val call = JSONObject()
                    call.put("number", cursor.getString(0) ?: "")
                    call.put("name", cursor.getString(1) ?: "")
                    call.put("date", dateFormat.format(Date(cursor.getLong(2))))
                    call.put("duration_seconds", cursor.getInt(3))
                    call.put("type", when (cursor.getInt(4)) {
                        CallLog.Calls.INCOMING_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE -> "missed"
                        else -> "other"
                    })
                    calls.put(call)
                }
            }

            val result = JSONObject()
            result.put("calls", calls)
            result.put("count", calls.length())
            ToolResult(name = toolName.displayName, result = result)
        } catch (e: SecurityException) {
            ToolResult(name = toolName.displayName, error = "통화 기록 접근 권한이 없습니다")
        } catch (e: Exception) {
            ToolResult(name = toolName.displayName, error = "통화 기록 조회 실패: ${e.message}")
        }
    }
}

@Singleton
class MakeCallExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ToolExecutor {

    override val toolName = ToolName.MAKE_CALL

    override suspend fun execute(arguments: Map<String, Any>): ToolResult {
        val number = arguments["number"]?.toString()
            ?: return ToolResult(name = toolName.displayName, error = "number is required")

        // Resolve contact name to number if needed
        val resolved = resolveContactNumber(number) ?: number

        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$resolved")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)

            val result = JSONObject()
            result.put("status", "calling")
            result.put("number_resolved", resolved)
            ToolResult(name = toolName.displayName, result = result)
        } catch (e: SecurityException) {
            ToolResult(name = toolName.displayName, error = "전화 권한이 없습니다")
        } catch (e: Exception) {
            ToolResult(name = toolName.displayName, error = "전화 걸기 실패: ${e.message}")
        }
    }

    private fun resolveContactNumber(nameOrNumber: String): String? {
        if (nameOrNumber.matches(Regex("[+\\d\\s\\-()]+"))) return null
        val projection = arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        context.contentResolver.query(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection, selection, arrayOf("%$nameOrNumber%"), null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd /Users/dgsw36/Desktop/01_프로젝트-개발/AI/Gemma4_Mobile/app && ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/tools/executor/CallLogExecutor.kt
git commit -m "feat(tools): add call log read and make call executors"
```

---

### Task 8: ContactsExecutor

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/tools/executor/ContactsExecutor.kt`

- [ ] **Step 1: ContactsExecutor.kt 생성**

```kotlin
// app/app/src/main/java/com/gemma4mobile/tools/executor/ContactsExecutor.kt
package com.gemma4mobile.tools.executor

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import com.gemma4mobile.tools.ToolExecutor
import com.gemma4mobile.tools.ToolName
import com.gemma4mobile.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactsReadExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ToolExecutor {

    override val toolName = ToolName.READ_CONTACTS

    override suspend fun execute(arguments: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val query = arguments["query"]?.toString()
                ?: return@withContext ToolResult(name = toolName.displayName, error = "query is required")
            val count = (arguments["count"] as? Number)?.toInt() ?: 10

            val contacts = JSONArray()
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$query%")

            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                selection, selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC LIMIT $count"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val contact = JSONObject()
                    contact.put("name", cursor.getString(0) ?: "")
                    contact.put("phone", cursor.getString(1) ?: "")
                    contacts.put(contact)
                }
            }

            // Also try to find email for each contact
            val result = JSONObject()
            result.put("contacts", contacts)
            result.put("count", contacts.length())
            ToolResult(name = toolName.displayName, result = result)
        } catch (e: SecurityException) {
            ToolResult(name = toolName.displayName, error = "연락처 접근 권한이 없습니다")
        } catch (e: Exception) {
            ToolResult(name = toolName.displayName, error = "연락처 조회 실패: ${e.message}")
        }
    }
}

@Singleton
class ContactsWriteExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ToolExecutor {

    override val toolName = ToolName.WRITE_CONTACT

    override suspend fun execute(arguments: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val name = arguments["name"]?.toString()
                ?: return@withContext ToolResult(name = toolName.displayName, error = "name is required")
            val phone = arguments["phone"]?.toString()
                ?: return@withContext ToolResult(name = toolName.displayName, error = "phone is required")
            val email = arguments["email"]?.toString()

            val ops = ArrayList<ContentProviderOperation>()

            // Insert raw contact
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )

            // Name
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build()
            )

            // Phone
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build()
            )

            // Email (optional)
            if (email != null) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Email.DATA, email)
                        .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_WORK)
                        .build()
                )
            }

            val results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            val contactId = results[0].uri?.lastPathSegment

            val result = JSONObject()
            result.put("contact_id", contactId)
            result.put("status", "created")
            result.put("name", name)
            ToolResult(name = toolName.displayName, result = result)
        } catch (e: SecurityException) {
            ToolResult(name = toolName.displayName, error = "연락처 쓰기 권한이 없습니다")
        } catch (e: Exception) {
            ToolResult(name = toolName.displayName, error = "연락처 추가 실패: ${e.message}")
        }
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd /Users/dgsw36/Desktop/01_프로젝트-개발/AI/Gemma4_Mobile/app && ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/tools/executor/ContactsExecutor.kt
git commit -m "feat(tools): add contacts read/write executors"
```

---

### Task 9: AlarmExecutor

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/tools/executor/AlarmExecutor.kt`

- [ ] **Step 1: AlarmExecutor.kt 생성**

```kotlin
// app/app/src/main/java/com/gemma4mobile/tools/executor/AlarmExecutor.kt
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
        // Android does not provide a standard ContentProvider for reading alarms.
        // We return a helpful message directing the user.
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
```

- [ ] **Step 2: 빌드 확인**

Run: `cd /Users/dgsw36/Desktop/01_프로젝트-개발/AI/Gemma4_Mobile/app && ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/tools/executor/AlarmExecutor.kt
git commit -m "feat(tools): add alarm read/set executors"
```

---

### Task 10: ToolPermissionManager

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/tools/ToolPermissionManager.kt`

- [ ] **Step 1: ToolPermissionManager.kt 생성**

```kotlin
// app/app/src/main/java/com/gemma4mobile/tools/ToolPermissionManager.kt
package com.gemma4mobile.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolPermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * 해당 툴을 실행하기 위해 필요한 권한 목록을 반환.
     * 이미 허용된 권한은 제외하고, 아직 허용되지 않은 권한만 반환.
     */
    fun getMissingPermissions(toolName: ToolName): List<String> {
        val required = getRequiredPermissions(toolName)
        return required.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasPermissions(toolName: ToolName): Boolean {
        return getMissingPermissions(toolName).isEmpty()
    }

    private fun getRequiredPermissions(toolName: ToolName): List<String> = when (toolName) {
        ToolName.SEARCH_WEB -> emptyList() // INTERNET is a normal permission
        ToolName.READ_CALENDAR -> listOf(Manifest.permission.READ_CALENDAR)
        ToolName.WRITE_CALENDAR -> listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        ToolName.READ_SMS -> listOf(Manifest.permission.READ_SMS)
        ToolName.SEND_SMS -> listOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS)
        ToolName.READ_CALL_LOG -> listOf(Manifest.permission.READ_CALL_LOG)
        ToolName.MAKE_CALL -> listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS)
        ToolName.READ_CONTACTS -> listOf(Manifest.permission.READ_CONTACTS)
        ToolName.WRITE_CONTACT -> listOf(Manifest.permission.WRITE_CONTACTS)
        ToolName.READ_ALARMS -> emptyList()
        ToolName.SET_ALARM -> emptyList() // SET_ALARM is a normal permission
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd /Users/dgsw36/Desktop/01_프로젝트-개발/AI/Gemma4_Mobile/app && ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/tools/ToolPermissionManager.kt
git commit -m "feat(tools): add runtime permission manager for tool execution"
```

---

### Task 11: AndroidManifest 권한 추가

**Files:**
- Modify: `app/app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 권한 추가**

`AndroidManifest.xml`의 기존 권한 아래에 추가:

```xml
<!-- Tool system permissions -->
<uses-permission android:name="android.permission.READ_CALENDAR" />
<uses-permission android:name="android.permission.WRITE_CALENDAR" />
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.SEND_SMS" />
<uses-permission android:name="android.permission.READ_CALL_LOG" />
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.WRITE_CONTACTS" />
<uses-permission android:name="android.permission.SET_ALARM" />
```

- [ ] **Step 2: 빌드 확인**

Run: `cd /Users/dgsw36/Desktop/01_프로젝트-개발/AI/Gemma4_Mobile/app && ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add app/app/src/main/AndroidManifest.xml
git commit -m "feat(tools): add tool system permissions to manifest"
```

---

### Task 12: DI 등록 — 모든 Executor를 ToolRouter에 연결

**Files:**
- Modify: `app/app/src/main/java/com/gemma4mobile/di/AppModule.kt`

- [ ] **Step 1: AppModule.kt에 Executor 등록 추가**

AppModule에 executor 초기화 함수를 추가:

```kotlin
// 기존 import 외 추가
import com.gemma4mobile.tools.ToolRouter
import com.gemma4mobile.tools.ToolPermissionManager
import com.gemma4mobile.tools.executor.*
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// AppModule object 내부에 추가 (기존 provideOkHttpClient, provideToolRouter 교체)

@Provides
@Singleton
fun provideOkHttpClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

@Provides
@Singleton
fun provideToolRouter(
    webSearch: WebSearchExecutor,
    calendarRead: CalendarReadExecutor,
    calendarWrite: CalendarWriteExecutor,
    smsRead: SmsReadExecutor,
    smsSend: SmsSendExecutor,
    callLogRead: CallLogReadExecutor,
    makeCall: MakeCallExecutor,
    contactsRead: ContactsReadExecutor,
    contactsWrite: ContactsWriteExecutor,
    alarmRead: AlarmReadExecutor,
    alarmSet: AlarmSetExecutor,
): ToolRouter {
    val router = ToolRouter()
    router.register(webSearch)
    router.register(calendarRead)
    router.register(calendarWrite)
    router.register(smsRead)
    router.register(smsSend)
    router.register(callLogRead)
    router.register(makeCall)
    router.register(contactsRead)
    router.register(contactsWrite)
    router.register(alarmRead)
    router.register(alarmSet)
    return router
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd /Users/dgsw36/Desktop/01_프로젝트-개발/AI/Gemma4_Mobile/app && ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/di/AppModule.kt
git commit -m "feat(tools): register all tool executors in DI module"
```

---

### Task 13: ChatViewModel 툴 체이닝 통합

**Files:**
- Modify: `app/app/src/main/java/com/gemma4mobile/chat/ChatViewModel.kt`

- [ ] **Step 1: ChatUiState에 툴 상태 추가 + sendMessage를 툴 체이닝 루프로 교체**

`ChatViewModel.kt` 전체를 다음으로 교체:

```kotlin
package com.gemma4mobile.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gemma4mobile.db.ChatMessage
import com.gemma4mobile.db.ChatSession
import com.gemma4mobile.inference.InferenceState
import com.gemma4mobile.inference.Turn
import com.gemma4mobile.model.ModelManager
import com.gemma4mobile.tools.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ToolCallUiInfo(
    val toolName: ToolName,
    val arguments: Map<String, Any>,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val modelTier: String = "",
    val error: String? = null,
    val toolStatus: ToolStatus = ToolStatus(),
    val pendingConfirmation: ToolCallUiInfo? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val modelManager: ModelManager,
    private val toolRouter: ToolRouter,
    private val permissionManager: ToolPermissionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    private val _sessions = MutableStateFlow<Map<String, List<ChatSession>>>(emptyMap())
    val sessions: StateFlow<Map<String, List<ChatSession>>> = _sessions.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Permissions that need to be requested — UI observes this
    private val _permissionRequest = MutableSharedFlow<List<String>>()
    val permissionRequest: SharedFlow<List<String>> = _permissionRequest.asSharedFlow()

    private var messagesJob: Job? = null
    private var pendingToolCall: ToolCall? = null

    init {
        viewModelScope.launch {
            val session = repository.createSession()
            _currentSessionId.value = session.id
            _uiState.update {
                it.copy(modelTier = modelManager.currentTier.value?.displayName ?: "")
            }
            observeMessages(session.id)
        }
        viewModelScope.launch {
            _searchQuery.collectLatest { query ->
                val sessionsFlow = if (query.isBlank()) {
                    repository.getSessions()
                } else {
                    repository.searchSessions(query)
                }
                sessionsFlow.collect { sessionList ->
                    _sessions.value = repository.groupSessionsByDate(sessionList)
                }
            }
        }
    }

    private fun observeMessages(sessionId: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repository.getMessages(sessionId).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun switchSession(sessionId: String) {
        _currentSessionId.value = sessionId
        _uiState.update { it.copy(messages = emptyList(), streamingText = "", isGenerating = false) }
        observeMessages(sessionId)
    }

    fun createNewSession() {
        viewModelScope.launch {
            val session = repository.createSession()
            _currentSessionId.value = session.id
            _uiState.update { it.copy(messages = emptyList(), streamingText = "", isGenerating = false) }
            observeMessages(session.id)
        }
    }

    fun renameSession(sessionId: String, title: String) {
        viewModelScope.launch {
            repository.renameSession(sessionId, title)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(ChatSession(id = sessionId, title = ""))
            if (_currentSessionId.value == sessionId) {
                createNewSession()
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun sendMessage(text: String) {
        val sessionId = _currentSessionId.value ?: return
        if (modelManager.inferenceState != InferenceState.READY) return

        viewModelScope.launch {
            repository.addMessage(sessionId, "user", text)

            val systemPrompt = SystemPromptBuilder.build("")
            val history = _uiState.value.messages.map { Turn(it.role, it.content) }

            _uiState.update { it.copy(isGenerating = true, streamingText = "") }

            runInferenceWithToolLoop(sessionId, text, systemPrompt, history)
        }
    }

    private suspend fun runInferenceWithToolLoop(
        sessionId: String,
        initialPrompt: String,
        systemPrompt: String,
        initialHistory: List<Turn>,
    ) {
        var currentPrompt = initialPrompt
        var conversationContext = StringBuilder()
        // Prepend system prompt to first message
        conversationContext.append("$systemPrompt\n\n")
        for (turn in initialHistory) {
            conversationContext.append("${turn.role}: ${turn.content}\n")
        }
        conversationContext.append("user: $currentPrompt\n")

        var chainCount = 0

        try {
            while (chainCount <= ToolRouter.MAX_CHAIN_COUNT) {
                val fullResponse = StringBuilder()

                modelManager.engine.generateStream(conversationContext.toString()).collect { token ->
                    fullResponse.append(token)
                    val displayText = if (ToolCallParser.hasOpenToolCall(fullResponse.toString())) {
                        ToolCallParser.extractText(fullResponse.toString())
                    } else {
                        fullResponse.toString()
                    }
                    _uiState.update { it.copy(streamingText = displayText) }
                }

                val output = fullResponse.toString()
                val toolCalls = ToolCallParser.parse(output)

                if (toolCalls.isEmpty()) {
                    // No tool calls — final response
                    val finalText = ToolCallParser.extractText(output)
                    repository.addMessage(sessionId, "model", finalText)
                    break
                }

                // Execute tool calls
                val toolResultsText = StringBuilder()
                for (tc in toolCalls) {
                    val tn = toolRouter.getToolName(tc.name)

                    // Check permissions
                    if (tn != null && !permissionManager.hasPermissions(tn)) {
                        val missing = permissionManager.getMissingPermissions(tn)
                        _permissionRequest.emit(missing)
                        val errorResult = ToolResult(name = tc.name, error = "권한이 필요합니다: ${missing.joinToString()}")
                        toolResultsText.append(errorResult.toXml()).append("\n")
                        continue
                    }

                    // Check confirmation requirement
                    if (tn != null && tn.requiresConfirmation) {
                        _uiState.update {
                            it.copy(pendingConfirmation = ToolCallUiInfo(tn, tc.arguments))
                        }
                        pendingToolCall = tc
                        // Pause the loop — will be resumed by confirmToolExecution() or denyToolExecution()
                        return
                    }

                    // Execute
                    _uiState.update { it.copy(toolStatus = ToolStatus(tn, true)) }
                    val result = toolRouter.execute(tc)
                    _uiState.update { it.copy(toolStatus = ToolStatus()) }
                    toolResultsText.append(result.toXml()).append("\n")
                }

                // Append tool results to context and continue loop
                conversationContext.append("model: $output\n")
                conversationContext.append(toolResultsText.toString())
                chainCount++
            }

            if (chainCount > ToolRouter.MAX_CHAIN_COUNT) {
                repository.addMessage(sessionId, "model", "요청을 처리하는 데 너무 많은 단계가 필요합니다. 질문을 더 간단하게 해주세요.")
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = e.message) }
        } finally {
            _uiState.update { it.copy(isGenerating = false, streamingText = "", toolStatus = ToolStatus()) }
        }
    }

    fun confirmToolExecution() {
        val tc = pendingToolCall ?: return
        val sessionId = _currentSessionId.value ?: return
        pendingToolCall = null
        _uiState.update { it.copy(pendingConfirmation = null) }

        viewModelScope.launch {
            val tn = toolRouter.getToolName(tc.name)
            _uiState.update { it.copy(toolStatus = ToolStatus(tn, true)) }
            val result = toolRouter.execute(tc)
            _uiState.update { it.copy(toolStatus = ToolStatus()) }

            // Save result and continue — for simplicity, show result as model message
            val resultText = result.result?.toString(2) ?: result.error ?: ""
            repository.addMessage(sessionId, "model", "✅ ${tn?.statusMessage ?: tc.name} 완료\n$resultText")
            _uiState.update { it.copy(isGenerating = false, streamingText = "") }
        }
    }

    fun denyToolExecution() {
        pendingToolCall = null
        val sessionId = _currentSessionId.value ?: return

        viewModelScope.launch {
            repository.addMessage(sessionId, "model", "실행이 취소되었습니다.")
            _uiState.update {
                it.copy(
                    pendingConfirmation = null,
                    isGenerating = false,
                    streamingText = "",
                )
            }
        }
    }

    fun onPermissionsResult(granted: Boolean) {
        if (!granted) {
            val sessionId = _currentSessionId.value ?: return
            viewModelScope.launch {
                repository.addMessage(sessionId, "model", "필요한 권한이 거부되어 기능을 실행할 수 없습니다.")
                _uiState.update { it.copy(isGenerating = false, streamingText = "") }
            }
        }
        // If granted, the user needs to retry the message
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd /Users/dgsw36/Desktop/01_프로젝트-개발/AI/Gemma4_Mobile/app && ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/chat/ChatViewModel.kt
git commit -m "feat(tools): integrate tool chaining loop into ChatViewModel"
```

---

### Task 14: 리치 카드 UI 컴포넌트

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/tools/ui/ToolResultCards.kt`
- Create: `app/app/src/main/java/com/gemma4mobile/tools/ui/ToolStatusIndicator.kt`
- Create: `app/app/src/main/java/com/gemma4mobile/tools/ui/ConfirmationSheet.kt`

- [ ] **Step 1: ToolStatusIndicator.kt 생성**

```kotlin
// app/app/src/main/java/com/gemma4mobile/tools/ui/ToolStatusIndicator.kt
package com.gemma4mobile.tools.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.gemma4mobile.tools.ToolStatus
import com.gemma4mobile.ui.theme.GemmaTheme

@Composable
fun ToolStatusIndicator(
    toolStatus: ToolStatus,
    modifier: Modifier = Modifier,
) {
    if (!toolStatus.isExecuting || toolStatus.toolName == null) return

    val infiniteTransition = rememberInfiniteTransition(label = "tool_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tool_alpha",
    )

    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = GemmaTheme.gemmaColors.aiIcon,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = toolStatus.toolName.statusMessage,
            style = MaterialTheme.typography.bodySmall,
            color = GemmaTheme.gemmaColors.aiIcon,
            modifier = Modifier.alpha(alpha),
        )
    }
}
```

- [ ] **Step 2: ToolResultCards.kt 생성**

```kotlin
// app/app/src/main/java/com/gemma4mobile/tools/ui/ToolResultCards.kt
package com.gemma4mobile.tools.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gemma4mobile.ui.theme.GemmaTheme

@Composable
fun SearchResultCard(
    title: String,
    snippet: String,
    url: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = GemmaTheme.gemmaColors.aiIcon,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (snippet.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = Uri.parse(url).host ?: url,
                style = MaterialTheme.typography.labelSmall,
                color = GemmaTheme.gemmaColors.placeholder,
            )
        }
    }
}

@Composable
fun CalendarEventCard(
    title: String,
    start: String,
    end: String,
    location: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = GemmaTheme.gemmaColors.aiIcon,
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("$start ~ $end", style = MaterialTheme.typography.bodySmall, color = GemmaTheme.gemmaColors.placeholder)
                if (location.isNotBlank()) {
                    Text(location, style = MaterialTheme.typography.bodySmall, color = GemmaTheme.gemmaColors.placeholder)
                }
            }
        }
    }
}

@Composable
fun ContactCard(
    name: String,
    phone: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = GemmaTheme.gemmaColors.aiIcon,
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(phone, style = MaterialTheme.typography.bodySmall, color = GemmaTheme.gemmaColors.placeholder)
            }
        }
    }
}

@Composable
fun CallLogCard(
    name: String,
    number: String,
    date: String,
    durationSeconds: Int,
    type: String,
    modifier: Modifier = Modifier,
) {
    val icon = when (type) {
        "incoming" -> Icons.Default.CallReceived
        "outgoing" -> Icons.Default.CallMade
        "missed" -> Icons.Default.CallMissed
        else -> Icons.Default.Phone
    }
    val typeLabel = when (type) {
        "incoming" -> "수신"
        "outgoing" -> "발신"
        "missed" -> "부재중"
        else -> type
    }
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = GemmaTheme.gemmaColors.aiIcon)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name.ifBlank { number },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "$typeLabel · $date · ${durationSeconds / 60}분 ${durationSeconds % 60}초",
                    style = MaterialTheme.typography.bodySmall,
                    color = GemmaTheme.gemmaColors.placeholder,
                )
            }
        }
    }
}

@Composable
fun SmsCard(
    address: String,
    body: String,
    date: String,
    type: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                if (type == "sent") Icons.Default.Send else Icons.Default.Email,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = GemmaTheme.gemmaColors.aiIcon,
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Row {
                    Text(address, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(date, style = MaterialTheme.typography.labelSmall, color = GemmaTheme.gemmaColors.placeholder)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
```

- [ ] **Step 3: ConfirmationSheet.kt 생성**

```kotlin
// app/app/src/main/java/com/gemma4mobile/tools/ui/ConfirmationSheet.kt
package com.gemma4mobile.tools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gemma4mobile.chat.ToolCallUiInfo
import com.gemma4mobile.tools.ToolName
import com.gemma4mobile.ui.theme.GemmaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmationSheet(
    info: ToolCallUiInfo,
    onConfirm: () -> Unit,
    onDeny: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDeny,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = when (info.toolName) {
                    ToolName.SEND_SMS -> "SMS 발송"
                    ToolName.MAKE_CALL -> "전화 걸기"
                    else -> "작업 실행"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))

            // Details
            val detailText = when (info.toolName) {
                ToolName.SEND_SMS -> {
                    val to = info.arguments["to"]?.toString() ?: ""
                    val msg = info.arguments["message"]?.toString() ?: ""
                    "받는 사람: $to\n내용: $msg"
                }
                ToolName.MAKE_CALL -> {
                    val number = info.arguments["number"]?.toString() ?: ""
                    "전화번호: $number"
                }
                else -> info.arguments.toString()
            }
            Text(
                text = detailText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDeny,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("취소")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("실행")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
```

- [ ] **Step 4: 빌드 확인**

Run: `cd /Users/dgsw36/Desktop/01_프로젝트-개발/AI/Gemma4_Mobile/app && ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/tools/ui/
git commit -m "feat(tools): add rich card UI components, status indicator, and confirmation sheet"
```

---

### Task 15: ChatScreen에 툴 UI 통합

**Files:**
- Modify: `app/app/src/main/java/com/gemma4mobile/ui/chat/ChatScreen.kt`

- [ ] **Step 1: ChatScreen에 ToolStatusIndicator + ConfirmationSheet + 권한 요청 통합**

`ChatScreen.kt`의 `ChatScreen` composable을 수정:

```kotlin
// ChatScreen.kt 최상단 import에 추가
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.gemma4mobile.tools.ui.ConfirmationSheet
import com.gemma4mobile.tools.ui.ToolStatusIndicator

// ChatScreen composable 교체
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit = {},
    onNewChat: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        viewModel.onPermissionsResult(allGranted)
    }

    // Observe permission requests
    LaunchedEffect(Unit) {
        viewModel.permissionRequest.collect { permissions ->
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    // Confirmation sheet
    uiState.pendingConfirmation?.let { info ->
        ConfirmationSheet(
            info = info,
            onConfirm = viewModel::confirmToolExecution,
            onDeny = viewModel::denyToolExecution,
        )
    }

    ChatScreenContent(
        uiState = uiState,
        onSendMessage = viewModel::sendMessage,
        onOpenDrawer = onOpenDrawer,
        onNewChat = onNewChat,
    )
}
```

- [ ] **Step 2: ChatScreenContent의 메시지 목록 영역에 ToolStatusIndicator 추가**

`ChatScreenContent` 안의 LazyColumn 영역에서, 스트리밍 텍스트 item 아래에 추가:

```kotlin
// LazyColumn 안, streamingText item 뒤에 추가:
if (uiState.toolStatus.isExecuting) {
    item {
        ToolStatusIndicator(toolStatus = uiState.toolStatus)
    }
}
```

- [ ] **Step 3: 빌드 확인**

Run: `cd /Users/dgsw36/Desktop/01_프로젝트-개발/AI/Gemma4_Mobile/app && ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/ui/chat/ChatScreen.kt
git commit -m "feat(tools): integrate tool status indicator and confirmation sheet into ChatScreen"
```

---

### Task 16: 시스템 프롬프트 프리셋에 툴 어시스턴트 추가

**Files:**
- Modify: `app/app/src/main/java/com/gemma4mobile/settings/SystemPromptPresets.kt`

- [ ] **Step 1: 툴 어시스턴트 프리셋 추가**

`SystemPromptPresets.kt`의 presets 리스트에 추가:

```kotlin
Preset("AI 어시스턴트 (툴 사용)", "검색, 캘린더, 메시지, 전화, 연락처, 알람 등의 도구를 적극적으로 활용하여 사용자의 요청을 처리합니다. 정보가 필요하면 검색하고, 일정 관련 요청이면 캘린더를 확인하세요."),
```

- [ ] **Step 2: 빌드 확인**

Run: `cd /Users/dgsw36/Desktop/01_프로젝트-개발/AI/Gemma4_Mobile/app && ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/settings/SystemPromptPresets.kt
git commit -m "feat(tools): add tool assistant system prompt preset"
```

---

### Task 17: 통합 테스트 + 최종 빌드 확인

**Files:**
- No new files

- [ ] **Step 1: 전체 빌드 (debug APK)**

Run: `cd /Users/dgsw36/Desktop/01_프로젝트-개발/AI/Gemma4_Mobile/app && ./gradlew assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 컴파일 에러가 있으면 수정**

에러 메시지를 확인하고 해당 파일을 수정. 일반적인 이슈:
- import 누락 → 추가
- 타입 불일치 → 수정
- Hilt 주입 누락 → `@Inject` 확인

- [ ] **Step 3: 최종 커밋**

```bash
git add -A
git commit -m "feat(tools): complete tool system integration - web search, calendar, SMS, calls, contacts, alarms"
```
