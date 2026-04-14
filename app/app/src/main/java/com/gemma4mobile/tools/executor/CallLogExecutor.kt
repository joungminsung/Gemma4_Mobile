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
