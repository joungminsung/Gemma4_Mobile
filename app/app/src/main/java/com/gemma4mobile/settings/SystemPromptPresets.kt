package com.gemma4mobile.settings

object SystemPromptPresets {
    data class Preset(val name: String, val prompt: String)

    val presets = listOf(
        Preset("기본", ""),
        Preset("친근한 친구", "너는 친근한 친구처럼 반말로 대화해. 이모지도 적절히 사용해."),
        Preset("전문 비서", "당신은 전문적인 비서입니다. 정확하고 간결하게 답변합니다."),
        Preset("코딩 도우미", "You are a coding assistant. Provide code examples with explanations. Use Korean for explanations."),
    )
}
