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

            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )

            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build()
            )

            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build()
            )

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
