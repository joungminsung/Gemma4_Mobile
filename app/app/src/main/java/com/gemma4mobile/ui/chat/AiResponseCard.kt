package com.gemma4mobile.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gemma4mobile.ui.theme.GemmaTheme

@Composable
fun AiResponseCard(
    content: String,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        // AI icon
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(GemmaTheme.gemmaColors.aiIcon),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = "AI",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // 실시간 마크다운 렌더링 — 스트리밍 중에도 적용
            MarkdownContent(text = content)

            // Action buttons (only after streaming completes)
            if (!isStreaming && content.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("response", content))
                            Toast.makeText(context, "복사됨", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Outlined.ContentCopy, "복사",
                            tint = GemmaTheme.gemmaColors.placeholder,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, content)
                            }
                            context.startActivity(Intent.createChooser(intent, "공유"))
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Share, "공유",
                            tint = GemmaTheme.gemmaColors.placeholder,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
