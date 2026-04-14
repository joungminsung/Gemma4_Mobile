package com.gemma4mobile.ui.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

@Composable
fun MarkdownContent(text: String, modifier: Modifier = Modifier) {
    Markdown(
        content = text,
        colors = markdownColor(
            text = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        typography = markdownTypography(
            text = MaterialTheme.typography.bodyLarge,
        ),
        modifier = modifier,
    )
}
