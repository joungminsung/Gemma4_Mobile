package com.gemma4mobile.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CodeBlock(
    code: String,
    language: String = "",
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0d0d0d)),
    ) {
        // Header: language + copy button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2d2d2d))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = language.ifEmpty { "code" },
                color = Color(0xFF8e8ea0),
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
                    Toast.makeText(context, "복사됨", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Outlined.ContentCopy, "복사",
                    tint = Color(0xFF8e8ea0),
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        // Code body
        SelectionContainer {
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = Color(0xFFd1d5db),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            )
        }
    }
}
