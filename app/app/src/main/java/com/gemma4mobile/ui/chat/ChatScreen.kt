package com.gemma4mobile.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gemma4mobile.chat.ChatUiState
import com.gemma4mobile.chat.ChatViewModel

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    ChatScreenContent(
        uiState = uiState,
        onSendMessage = viewModel::sendMessage,
    )
}

@Composable
fun ChatScreenContent(
    uiState: ChatUiState,
    onSendMessage: (String) -> Unit,
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size, uiState.streamingText) {
        if (uiState.messages.isNotEmpty() || uiState.streamingText.isNotEmpty()) {
            listState.animateScrollToItem(
                uiState.messages.size + if (uiState.streamingText.isNotEmpty()) 1 else 0
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ModelStatusBar(
            tierName = uiState.modelTier,
            isGenerating = uiState.isGenerating,
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            items(uiState.messages) { message ->
                MessageBubble(
                    content = message.content,
                    isUser = message.role == "user",
                )
            }

            if (uiState.streamingText.isNotEmpty()) {
                item {
                    MessageBubble(
                        content = uiState.streamingText,
                        isUser = false,
                    )
                }
            }
        }

        uiState.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Surface(tonalElevation = 3.dp) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("메시지를 입력하세요") },
                    maxLines = 4,
                    enabled = !uiState.isGenerating,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText.trim())
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !uiState.isGenerating,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "전송",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
