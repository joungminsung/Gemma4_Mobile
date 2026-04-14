package com.gemma4mobile.ui.drawer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gemma4mobile.db.ChatSession
import com.gemma4mobile.ui.theme.GemmaTheme

@Composable
fun SessionDrawer(
    sessions: Map<String, List<ChatSession>>,
    currentSessionId: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSessionClick: (String) -> Unit,
    onNewChat: () -> Unit,
    onRenameSession: (String, String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        drawerContainerColor = GemmaTheme.gemmaColors.sidebarBackground,
        modifier = modifier.width(280.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Gemma 4",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onNewChat) {
                    Icon(Icons.Default.Edit, "새 대화", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            // Search
            SessionSearch(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            Spacer(Modifier.height(8.dp))

            // Session list
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                val dateOrder = listOf("오늘", "어제", "이번 주", "이전")
                for (dateGroup in dateOrder) {
                    val groupSessions = sessions[dateGroup] ?: continue
                    item {
                        Text(
                            dateGroup,
                            style = MaterialTheme.typography.labelSmall,
                            color = GemmaTheme.gemmaColors.placeholder,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                    }
                    items(groupSessions, key = { it.id }) { session ->
                        SessionItem(
                            session = session,
                            isSelected = session.id == currentSessionId,
                            onClick = { onSessionClick(session.id) },
                            onRename = { newTitle -> onRenameSession(session.id, newTitle) },
                            onDelete = { onDeleteSession(session.id) },
                        )
                    }
                }
            }

            // Settings button
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            TextButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Default.Settings, "설정", modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("설정")
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
