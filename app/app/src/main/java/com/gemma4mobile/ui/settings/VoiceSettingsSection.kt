package com.gemma4mobile.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gemma4mobile.settings.AppSettings
import com.gemma4mobile.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsSection(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val languages = listOf("ko-KR" to "한국어", "en-US" to "영어", "auto" to "자동")

    SettingsCard(title = "음성 입력", modifier = modifier) {
        // STT Language
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = languages.find { it.first == settings.sttLanguage }?.second ?: "한국어",
                onValueChange = {},
                readOnly = true,
                label = { Text("인식 언어") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                languages.forEach { (code, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = { viewModel.updateSttLanguage(code); expanded = false },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Auto-send toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("자동 전송", style = MaterialTheme.typography.bodyMedium)
                Text("음성 인식 완료 시 자동 전송", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = settings.autoSendVoice, onCheckedChange = { viewModel.updateAutoSendVoice(it) })
        }
    }
}
