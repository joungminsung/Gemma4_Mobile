package com.gemma4mobile.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gemma4mobile.model.DownloadState
import com.gemma4mobile.model.ModelManager
import com.gemma4mobile.model.ModelTier
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun OnboardingScreen(
    modelManager: ModelManager,
    onModelReady: () -> Unit,
) {
    val recommended = modelManager.recommendedTier
    val available = modelManager.availableTiers
    var selectedTier by remember { mutableStateOf(recommended) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var isDownloading by remember { mutableStateOf(false) }
    var devMode by remember { mutableStateOf(false) }
    var devModelPath by remember { mutableStateOf(ModelManager.DEV_MODEL_PATH) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Gemma 4 Mobile",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "온디바이스 AI 어시스턴트",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        // 개발자 모드 토글
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "개발자 모드 (로컬 모델)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Switch(checked = devMode, onCheckedChange = { devMode = it })
        }

        if (devMode) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = devModelPath,
                onValueChange = { devModelPath = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("모델 파일 경로") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))

            val fileExists = remember(devModelPath) { File(devModelPath).exists() }

            Text(
                text = if (fileExists) "모델 파일 발견" else "파일 없음 — adb push로 먼저 넣어주세요",
                style = MaterialTheme.typography.bodySmall,
                color = if (fileExists) MaterialTheme.colorScheme.secondary
                       else MaterialTheme.colorScheme.error,
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    error = null
                    try {
                        modelManager.loadModelFromPath(devModelPath)
                        onModelReady()
                    } catch (e: Exception) {
                        error = "모델 로드 실패: ${e.message}"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = fileExists,
            ) {
                Text("로컬 모델로 시작")
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            return
        }

        // ─── 일반 모드 ───

        if (recommended == null) {
            Text(
                text = "이 디바이스는 최소 사양(RAM 4GB)을 충족하지 않습니다.",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            return
        }

        Text("모델 선택", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        available.forEach { tier ->
            val isSelected = tier == selectedTier
            val alreadyDownloaded = modelManager.isModelDownloaded(tier)

            OutlinedCard(
                onClick = { if (!isDownloading) selectedTier = tier },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = isSelected, onClick = { selectedTier = tier })
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(tier.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "${tier.downloadSizeMb}MB" +
                                if (alreadyDownloaded) " (다운로드 완료)" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (tier == recommended) {
                        Spacer(Modifier.weight(1f))
                        AssistChip(
                            onClick = {},
                            label = { Text("추천") },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        if (isDownloading) {
            LinearProgressIndicator(
                progress = { downloadProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text("다운로드 중... $downloadProgress%")
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                val tier = selectedTier ?: return@Button
                if (modelManager.isModelDownloaded(tier)) {
                    modelManager.loadModel(tier)
                    onModelReady()
                } else {
                    isDownloading = true
                    error = null
                    scope.launch {
                        modelManager.downloadModel(tier).collect { state ->
                            when (state) {
                                is DownloadState.Progress -> downloadProgress = state.percent
                                is DownloadState.Complete -> {
                                    isDownloading = false
                                    modelManager.loadModel(tier)
                                    onModelReady()
                                }
                                is DownloadState.Error -> {
                                    isDownloading = false
                                    error = state.message
                                }
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedTier != null && !isDownloading,
        ) {
            Text(
                if (selectedTier != null && modelManager.isModelDownloaded(selectedTier!!)) {
                    "시작하기"
                } else {
                    "다운로드 후 시작"
                }
            )
        }
    }
}
