package com.gemma4mobile.assist

import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * 삼성 측면 버튼 등 VoiceInteractionService 기반 어시스턴트 호출 지원.
 */
class GemmaVoiceInteractionService : VoiceInteractionService()

class GemmaVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return GemmaVoiceInteractionSession(this)
    }
}
