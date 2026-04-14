package com.gemma4mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.gemma4mobile.chat.ChatViewModel
import com.gemma4mobile.model.ModelManager
import com.gemma4mobile.settings.AppSettings
import com.gemma4mobile.settings.SettingsRepository
import com.gemma4mobile.settings.ThemeMode
import com.gemma4mobile.ui.chat.ChatScreen
import com.gemma4mobile.ui.drawer.SessionDrawer
import com.gemma4mobile.ui.onboarding.OnboardingScreen
import com.gemma4mobile.ui.settings.SettingsScreen
import com.gemma4mobile.ui.theme.Gemma4MobileTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var modelManager: ModelManager
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val fromAssist = intent?.getBooleanExtra(AssistActivity.EXTRA_FROM_ASSIST, false) ?: false

        setContent {
            val settings by settingsRepository.settingsFlow.collectAsState(
                initial = AppSettings()
            )

            val isDark = when (settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            Gemma4MobileTheme(darkTheme = isDark) {
                var modelReady by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }
                var autoLoading by remember { mutableStateOf(true) }

                // 앱 시작 시 마지막 모델 자동 로드
                LaunchedEffect(Unit) {
                    settingsRepository.lastModelPath.collect { path ->
                        if (path != null && !modelReady && autoLoading) {
                            try {
                                modelManager.loadModelFromInternalPath(path)
                                modelReady = true
                            } catch (_: Exception) {
                                // 저장된 경로의 모델이 없으면 온보딩으로
                            }
                            autoLoading = false
                        } else {
                            autoLoading = false
                        }
                    }
                }

                when {
                    autoLoading -> {
                        // 자동 로드 중 로딩 화면
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    }
                    !modelReady -> {
                        OnboardingScreen(
                            modelManager = modelManager,
                            settingsRepository = settingsRepository,
                            onModelReady = { modelReady = true },
                        )
                    }
                    showSettings -> {
                        SettingsScreen(onBack = { showSettings = false })
                    }
                    else -> {
                        MainChatWithDrawer(
                            onSettingsClick = { showSettings = true },
                            fromAssist = fromAssist,
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        modelManager.unloadModel()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChatWithDrawer(
    onSettingsClick: () -> Unit,
    fromAssist: Boolean = false,
    chatViewModel: ChatViewModel = hiltViewModel(),
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val sessions by chatViewModel.sessions.collectAsState()
    val currentSessionId by chatViewModel.currentSessionId.collectAsState()
    val searchQuery by chatViewModel.searchQuery.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SessionDrawer(
                sessions = sessions,
                currentSessionId = currentSessionId,
                searchQuery = searchQuery,
                onSearchQueryChange = chatViewModel::updateSearchQuery,
                onSessionClick = { id ->
                    chatViewModel.switchSession(id)
                    scope.launch { drawerState.close() }
                },
                onNewChat = {
                    chatViewModel.createNewSession()
                    scope.launch { drawerState.close() }
                },
                onRenameSession = chatViewModel::renameSession,
                onDeleteSession = chatViewModel::deleteSession,
                onSettingsClick = {
                    scope.launch { drawerState.close() }
                    onSettingsClick()
                },
            )
        },
    ) {
        ChatScreen(
            viewModel = chatViewModel,
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onNewChat = { chatViewModel.createNewSession() },
        )
    }
}
