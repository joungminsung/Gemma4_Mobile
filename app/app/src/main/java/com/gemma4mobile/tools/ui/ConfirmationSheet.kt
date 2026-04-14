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
