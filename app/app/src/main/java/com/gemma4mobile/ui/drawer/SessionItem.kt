package com.gemma4mobile.ui.drawer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gemma4mobile.db.ChatSession

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionItem(
    session: ChatSession,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true },
                ),
            color = if (isSelected) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.background.copy(alpha = 0f)
            },
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = session.title.ifEmpty { "새 대화" },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("이름 변경") },
                onClick = { showMenu = false; showRenameDialog = true },
            )
            DropdownMenuItem(
                text = { Text("삭제", color = MaterialTheme.colorScheme.error) },
                onClick = { showMenu = false; onDelete() },
            )
        }
    }

    if (showRenameDialog) {
        var newTitle by remember { mutableStateOf(session.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("대화 이름 변경") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(newTitle); showRenameDialog = false }) {
                    Text("변경")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("취소") }
            },
        )
    }
}
