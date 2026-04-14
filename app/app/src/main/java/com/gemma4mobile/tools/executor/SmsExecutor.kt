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

            val selection = if (contact != null) "${Telephony.Sms.ADDRESS} LIKE ?" else null
            val selectionArgs = if (contact != null) arrayOf("%$contact%") else null

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
