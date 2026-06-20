package com.beancounter.agent.clients

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class DeepSeekNonThinkingTest {
    private val mapper = JsonMapper.builder().build()

    @Test
    fun `injects thinking disabled into the request body`() {
        val body = """{"model":"deepseek-v4-flash","messages":[],"stream":true}""".toByteArray()

        val result = mapper.readTree(DeepSeekNonThinking.disableThinking(body, mapper))

        assertThat(result.get("thinking").get("type").asString()).isEqualTo("disabled")
        // Original fields preserved.
        assertThat(result.get("model").asString()).isEqualTo("deepseek-v4-flash")
        assertThat(result.get("stream").asBoolean()).isTrue()
    }

    @Test
    fun `overwrites an existing thinking flag`() {
        val body = """{"model":"x","thinking":{"type":"enabled"}}""".toByteArray()

        val result = mapper.readTree(DeepSeekNonThinking.disableThinking(body, mapper))

        assertThat(result.get("thinking").get("type").asString()).isEqualTo("disabled")
    }

    @Test
    fun `returns the original body unchanged when it is not JSON`() {
        val body = "not json".toByteArray()

        assertThat(DeepSeekNonThinking.disableThinking(body, mapper)).isEqualTo(body)
    }
}