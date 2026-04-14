package com.gemma4mobile.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ModelSettingsSection(modifier: Modifier = Modifier) {
    SettingsCard(title = "모델 관리", modifier = modifier) {
        Text("현재 모델: 개발자 모드", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "모델 티어 변경과 다운로드는 향후 업데이트에서 지원됩니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
