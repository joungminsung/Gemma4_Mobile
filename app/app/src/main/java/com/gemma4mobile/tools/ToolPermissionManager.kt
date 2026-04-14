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
        ToolName.SEARCH_WEB -> emptyList()
        ToolName.READ_CALENDAR -> listOf(Manifest.permission.READ_CALENDAR)
        ToolName.WRITE_CALENDAR -> listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        ToolName.READ_SMS -> listOf(Manifest.permission.READ_SMS)
        ToolName.SEND_SMS -> listOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS)
        ToolName.READ_CALL_LOG -> listOf(Manifest.permission.READ_CALL_LOG)
        ToolName.MAKE_CALL -> listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS)
        ToolName.READ_CONTACTS -> listOf(Manifest.permission.READ_CONTACTS)
        ToolName.WRITE_CONTACT -> listOf(Manifest.permission.WRITE_CONTACTS)
        ToolName.READ_ALARMS -> emptyList()
        ToolName.SET_ALARM -> emptyList()
    }
}
