package com.gemma4mobile.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gemma4mobile.settings.AppSettings
import com.gemma4mobile.settings.SettingsViewModel
import kotlin.math.roundToInt

@Composable
fun GenerationSettingsSection(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    SettingsCard(title = "생성 파라미터", modifier = modifier) {
        // Temperature
        Text("Temperature: ${String.format("%.1f", settings.temperature)}", style = MaterialTheme.typography.bodyMedium)
        Text("높을수록 창의적, 낮을수록 정확", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = settings.temperature,
            onValueChange = { viewModel.updateTemperature(it) },
            valueRange = 0f..2f,
            steps = 19,
        )

        Spacer(Modifier.height(8.dp))

        // Top-P
        Text("Top-P: ${String.format("%.2f", settings.topP)}", style = MaterialTheme.typography.bodyMedium)
        Text("토큰 선택 확률 범위", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = settings.topP,
            onValueChange = { viewModel.updateTopP(it) },
            valueRange = 0f..1f,
            steps = 19,
        )

        Spacer(Modifier.height(8.dp))

        // Max Tokens
        Text("Max Tokens: ${settings.maxTokens}", style = MaterialTheme.typography.bodyMedium)
        Text("응답 최대 길이", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = settings.maxTokens.toFloat(),
            onValueChange = { viewModel.updateMaxTokens(it.roundToInt()) },
            valueRange = 64f..2048f,
            steps = 30,
        )
    }
}
