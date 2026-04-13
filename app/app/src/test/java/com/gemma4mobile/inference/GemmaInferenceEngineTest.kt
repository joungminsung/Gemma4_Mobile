package com.gemma4mobile.inference

import org.junit.Assert.*
import org.junit.Test

class GemmaInferenceEngineTest {

    @Test
    fun `formatPrompt wraps user message in Gemma turn format`() {
        val engine = GemmaInferenceEngine()
        val formatted = engine.formatPrompt("안녕하세요")
        assertEquals(
            "<start_of_turn>user\n안녕하세요<end_of_turn>\n<start_of_turn>model\n",
            formatted,
        )
    }

    @Test
    fun `formatPrompt with history includes previous turns`() {
        val engine = GemmaInferenceEngine()
        val history = listOf(
            Turn("user", "안녕"),
            Turn("model", "안녕하세요!"),
        )
        val formatted = engine.formatPrompt("잘 지내?", history)
        val expected = "<start_of_turn>user\n안녕<end_of_turn>\n" +
            "<start_of_turn>model\n안녕하세요!<end_of_turn>\n" +
            "<start_of_turn>user\n잘 지내?<end_of_turn>\n" +
            "<start_of_turn>model\n"
        assertEquals(expected, formatted)
    }

    @Test
    fun `state is UNLOADED initially`() {
        val engine = GemmaInferenceEngine()
        assertEquals(InferenceState.UNLOADED, engine.state)
    }
}
