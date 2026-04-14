package com.gemma4mobile.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gemma4mobile.settings.SystemPromptPresets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptSettingsSection(
    currentPrompt: String,
    onUpdatePrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var prompt by remember(currentPrompt) { mutableStateOf(currentPrompt) }
    var expanded by remember { mutableStateOf(false) }

    SettingsCard(title = "시스템 프롬프트", modifier = modifier) {
        // Preset dropdown
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = SystemPromptPresets.presets.find { it.prompt == prompt }?.name ?: "커스텀",
                onValueChange = {},
                readOnly = true,
                label = { Text("프리셋") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SystemPromptPresets.presets.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.name) },
                        onClick = {
                            prompt = preset.prompt
                            onUpdatePrompt(preset.prompt)
                            expanded = false
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Custom prompt input
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("커스텀 프롬프트") },
            minLines = 3,
            maxLines = 6,
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { onUpdatePrompt(prompt) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("저장")
        }
    }
}
