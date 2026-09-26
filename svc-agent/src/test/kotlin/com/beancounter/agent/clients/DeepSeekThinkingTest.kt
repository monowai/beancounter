package com.beancounter.agent.clients

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class DeepSeekThinkingTest {
    private val mapper = JsonMapper.builder().build()

    @Test
    fun `injects thinking disabled into the request body`() {
        val body = """{"model":"deepseek-flash","messages":[],"stream":true}""".toByteArray()

        val result = mapper.readTree(DeepSeekThinking.disableThinking(body, mapper))

        assertThat(result.get("thinking").get("type").asString()).isEqualTo("disabled")
        // Original fields preserved.
        assertThat(result.get("model").asString()).isEqualTo("deepseek-flash")
        assertThat(result.get("stream").asBoolean()).isTrue()
    }

    @Test
    fun `overwrites an existing thinking flag`() {
        val body = """{"model":"x","thinking":{"type":"enabled"}}""".toByteArray()

        val result = mapper.readTree(DeepSeekThinking.disableThinking(body, mapper))

        assertThat(result.get("thinking").get("type").asString()).isEqualTo("disabled")
    }

    @Test
    fun `returns the original body unchanged when it is not JSON`() {
        val body = "not json".toByteArray()

        assertThat(DeepSeekThinking.disableThinking(body, mapper)).isEqualTo(body)
        assertThat(DeepSeekThinking.lowEffort(body, mapper)).isEqualTo(body)
    }

    @Test
    fun `defaults reasoning effort to low when the request sets none`() {
        val body = """{"model":"deepseek-flash","messages":[]}""".toByteArray()

        val result = mapper.readTree(DeepSeekThinking.lowEffort(body, mapper))

        assertThat(result.get("reasoning_effort").asString()).isEqualTo("low")
        assertThat(result.get("model").asString()).isEqualTo("deepseek-flash")
    }

    @Test
    fun `keeps a reasoning effort the request already chose`() {
        // The deep tier asks for `high` explicitly; the low default must not clobber it.
        val body = """{"model":"deepseek-v4-pro","reasoning_effort":"high"}""".toByteArray()

        val result = mapper.readTree(DeepSeekThinking.lowEffort(body, mapper))

        assertThat(result.get("reasoning_effort").asString()).isEqualTo("high")
    }

    @Test
    fun `returns the original body instance when nothing changes`() {
        // Callers skip the content-length reset on an unchanged body.
        val body = """{"model":"deepseek-v4-pro","reasoning_effort":"high"}""".toByteArray()

        assertThat(DeepSeekThinking.lowEffort(body, mapper)).isSameAs(body)
    }
}