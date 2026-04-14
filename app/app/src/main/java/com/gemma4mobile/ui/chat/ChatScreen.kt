package com.gemma4mobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gemma4mobile.chat.ChatUiState
import com.gemma4mobile.chat.ChatViewModel
import com.gemma4mobile.ui.theme.GemmaTheme

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit = {},
    onNewChat: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    ChatScreenContent(
        uiState = uiState,
        onSendMessage = viewModel::sendMessage,
        onOpenDrawer = onOpenDrawer,
        onNewChat = onNewChat,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenContent(
    uiState: ChatUiState,
    onSendMessage: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size, uiState.streamingText) {
        val targetIndex = uiState.messages.size + if (uiState.streamingText.isNotEmpty()) 0 else -1
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Top bar
        TopAppBar(
            title = {
                Column {
                    Text(
                        uiState.modelTier.ifEmpty { "Gemma 4" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (uiState.isGenerating) {
                        Text(
                            "생성 중...",
                            style = MaterialTheme.typography.labelSmall,
                            color = GemmaTheme.gemmaColors.aiIcon,
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Default.Menu, "메뉴")
                }
            },
            actions = {
                IconButton(onClick = onNewChat) {
                    Icon(Icons.Default.Edit, "새 대화")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )

        // Messages
        if (uiState.messages.isEmpty() && uiState.streamingText.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✦", fontSize = 48.sp, color = GemmaTheme.gemmaColors.aiIcon)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "무엇이든 물어보세요",
                        style = MaterialTheme.typography.titleMedium,
                        color = GemmaTheme.gemmaColors.placeholder,
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                items(uiState.messages) { message ->
                    if (message.role == "user") {
                        UserMessageBubble(content = message.content)
                    } else {
                        AiResponseCard(content = message.content, isStreaming = false)
                    }
                }

                if (uiState.streamingText.isNotEmpty()) {
                    item {
                        AiResponseCard(content = uiState.streamingText, isStreaming = true)
                    }
                }
            }
        }

        // Error
        uiState.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // Input bar
        Surface(
            color = MaterialTheme.colorScheme.background,
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(GemmaTheme.gemmaColors.inputBackground)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "메시지를 입력하세요",
                            color = GemmaTheme.gemmaColors.placeholder,
                        )
                    },
                    maxLines = 4,
                    enabled = !uiState.isGenerating,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
                // Send button (visible when text is not empty)
                if (inputText.isNotBlank()) {
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !uiState.isGenerating) {
                                onSendMessage(inputText.trim())
                                inputText = ""
                            }
                        },
                        enabled = !uiState.isGenerating,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "전송",
                            tint = GemmaTheme.gemmaColors.aiIcon,
                        )
                    }
                }
                if (inputText.isBlank()) {
                    VoiceInputButton(
                        onResult = { text ->
                            inputText = text
                        },
                        onPartialResult = { text ->
                            inputText = text
                        },
                    )
                }
            }
        }
    }
}
