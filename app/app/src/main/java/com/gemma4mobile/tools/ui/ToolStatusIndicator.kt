package com.gemma4mobile.tools.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.gemma4mobile.chat.ThinkingStep
import com.gemma4mobile.tools.ToolStatus
import com.gemma4mobile.ui.theme.GemmaTheme

@Composable
fun ToolStatusIndicator(
    toolStatus: ToolStatus,
    modifier: Modifier = Modifier,
) {
    if (!toolStatus.isExecuting || toolStatus.toolName == null) return

    val infiniteTransition = rememberInfiniteTransition(label = "tool_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tool_alpha",
    )

    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = GemmaTheme.gemmaColors.aiIcon,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = toolStatus.toolName.statusMessage,
            style = MaterialTheme.typography.bodySmall,
            color = GemmaTheme.gemmaColors.aiIcon,
            modifier = Modifier.alpha(alpha),
        )
    }
}

@Composable
fun ThinkingStepsIndicator(
    steps: List<ThinkingStep>,
    modifier: Modifier = Modifier,
) {
    if (steps.isEmpty()) return

    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        steps.forEach { step ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(24.dp),
            ) {
                if (step.isDone) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = GemmaTheme.gemmaColors.aiIcon,
                    )
                } else {
                    val infiniteTransition = rememberInfiniteTransition(label = "step_pulse_${step.label}")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = EaseInOut),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "step_alpha",
                    )
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp).alpha(alpha),
                        strokeWidth = 1.5.dp,
                        color = GemmaTheme.gemmaColors.aiIcon,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = step.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (step.isDone) GemmaTheme.gemmaColors.placeholder else GemmaTheme.gemmaColors.aiIcon,
                )
            }
        }
    }
}
