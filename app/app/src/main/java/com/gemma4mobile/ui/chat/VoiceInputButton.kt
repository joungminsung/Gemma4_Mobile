package com.gemma4mobile.ui.chat

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gemma4mobile.ui.theme.GemmaTheme

@Composable
fun VoiceInputButton(
    language: String = "ko-KR",
    onResult: (String) -> Unit,
    onPartialResult: (String) -> Unit = {},
    onListeningChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) startListening(context, language, onResult, onPartialResult, { isListening = it; onListeningChange(it) })
    }

    // Pulse animation when listening
    val pulseScale by animateFloatAsState(
        targetValue = if (isListening) 1.3f else 1f,
        animationSpec = if (isListening) {
            infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            )
        } else {
            tween(300)
        },
        label = "pulse",
    )

    IconButton(
        onClick = {
            if (isListening) {
                isListening = false
                onListeningChange(false)
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        modifier = modifier.scale(pulseScale),
    ) {
        Icon(
            imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
            contentDescription = if (isListening) "중지" else "음성 입력",
            tint = if (isListening) Color(0xFFef4444) else GemmaTheme.gemmaColors.placeholder,
            modifier = Modifier.size(24.dp),
        )
    }
}

private fun startListening(
    context: android.content.Context,
    language: String,
    onResult: (String) -> Unit,
    onPartialResult: (String) -> Unit,
    onListeningChange: (Boolean) -> Unit,
) {
    val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language == "auto") "" else language)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    speechRecognizer.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { onListeningChange(true) }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { onListeningChange(false) }
        override fun onError(error: Int) { onListeningChange(false) }
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            matches?.firstOrNull()?.let { onResult(it) }
            onListeningChange(false)
            speechRecognizer.destroy()
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            matches?.firstOrNull()?.let { onPartialResult(it) }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    })

    speechRecognizer.startListening(intent)
}
