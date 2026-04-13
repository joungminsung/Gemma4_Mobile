package com.gemma4mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.gemma4mobile.model.ModelManager
import com.gemma4mobile.ui.chat.ChatScreen
import com.gemma4mobile.ui.onboarding.OnboardingScreen
import com.gemma4mobile.ui.theme.Gemma4MobileTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var modelManager: ModelManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Gemma4MobileTheme {
                var modelReady by remember { mutableStateOf(false) }

                if (modelReady) {
                    ChatScreen()
                } else {
                    OnboardingScreen(
                        modelManager = modelManager,
                        onModelReady = { modelReady = true },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        modelManager.unloadModel()
    }
}
