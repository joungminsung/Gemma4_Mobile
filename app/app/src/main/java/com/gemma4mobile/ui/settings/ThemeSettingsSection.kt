package com.gemma4mobile.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gemma4mobile.settings.ThemeMode

@Composable
fun ThemeSettingsSection(
    currentMode: ThemeMode,
    onUpdateMode: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(title = "테마", modifier = modifier) {
        ThemeMode.entries.forEach { mode ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = currentMode == mode, onClick = { onUpdateMode(mode) })
                Spacer(Modifier.width(8.dp))
                Text(
                    when (mode) {
                        ThemeMode.DARK -> "다크"
                        ThemeMode.LIGHT -> "라이트"
                        ThemeMode.SYSTEM -> "시스템 설정"
                    }
                )
            }
        }
    }
}
