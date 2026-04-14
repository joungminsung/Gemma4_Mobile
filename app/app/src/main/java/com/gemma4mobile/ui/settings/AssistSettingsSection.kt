package com.gemma4mobile.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AssistSettingsSection(
    enabled: Boolean,
    onUpdateEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(title = "빠른 호출", modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("측면 버튼 호출", style = MaterialTheme.typography.bodyMedium)
                Text("측면 버튼을 길게 눌러 호출", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = enabled, onCheckedChange = onUpdateEnabled)
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "기기 설정 → 앱 → 기본 앱 → 디지털 어시스턴트 앱에서 'Gemma4'를 선택하세요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
