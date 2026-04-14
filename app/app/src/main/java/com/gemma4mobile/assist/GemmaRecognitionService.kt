package com.gemma4mobile.assist

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService

/**
 * 디지털 어시스턴트 등록에 필요한 RecognitionService stub.
 * 실제 음성 인식은 SpeechRecognizer를 사용하므로 이 서비스는 빈 구현.
 */
class GemmaRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {}
    override fun onCancel(listener: Callback?) {}
    override fun onStopListening(listener: Callback?) {}
}
