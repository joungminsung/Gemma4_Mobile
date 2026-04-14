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
