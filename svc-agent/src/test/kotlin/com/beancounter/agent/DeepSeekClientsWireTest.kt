package com.beancounter.agent

import com.sun.net.httpserver.HttpServer
import io.micrometer.observation.ObservationRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.deepseek.DeepSeekChatOptions
import org.springframework.ai.model.tool.ToolCallingManager
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress

/**
 * What each DeepSeek client actually puts on the wire (beancounter#1128).
 *
 * The thinking client (Chat FAB) must ask for `reasoning_effort: low` — Spring
 * AI 2.0.1's ReasoningEffort enum has no LOW, so it is injected into the body.
 * The fast client must stay non-thinking and carry no effort, since any effort
 * value re-enables thinking on DeepSeek.
 */
class DeepSeekClientsWireTest {
    private val mapper = JsonMapper.builder().build()
    private lateinit var server: HttpServer
    private val bodies = mutableListOf<JsonNode>()

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            bodies.add(mapper.readTree(exchange.requestBody.readAllBytes()))
            val reply = COMPLETION.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, reply.size.toLong())
            exchange.responseBody.use { it.write(reply) }
        }
        server.start()
    }

    @AfterEach
    fun stop() = server.stop(0)

    private val config = ChatClientConfiguration()

    private fun client(thinking: Boolean): ChatClient {
        val baseUrl = "http://127.0.0.1:${server.address.port}"
        val toolCallingManager = ToolCallingManager.builder().build()
        return if (thinking) {
            config.deepSeekChatClient(
                "key",
                baseUrl,
                "deepseek-flash",
                0.2,
                4096,
                mapper,
                toolCallingManager,
                ObservationRegistry.NOOP
            )
        } else {
            config.fastDeepSeekChatClient(
                "key",
                baseUrl,
                "deepseek-flash",
                0.2,
                4096,
                mapper,
                toolCallingManager,
                ObservationRegistry.NOOP
            )
        }
    }

    @Test
    fun `thinking client asks DeepSeek for low reasoning effort`() {
        assertThat(client(thinking = true).prompt("hi").call().content()).isEqualTo("ok")

        val sent = bodies.single()
        assertThat(sent.get("reasoning_effort").asString()).isEqualTo("low")
        assertThat(sent.get("thinking")?.get("type")?.asString()).isNotEqualTo("disabled")
        assertThat(sent.get("model").asString()).isEqualTo("deepseek-flash")
    }

    @Test
    fun `fast client disables thinking and sends no reasoning effort`() {
        assertThat(client(thinking = false).prompt("hi").call().content()).isEqualTo("ok")

        val sent = bodies.single()
        assertThat(sent.get("thinking").get("type").asString()).isEqualTo("disabled")
        assertThat(sent.has("reasoning_effort")).isFalse()
    }

    @Test
    fun `fast client stays non-thinking when a call sets its own options`() {
        // AgentController.buildOptions passes per-call DeepSeek options on every turn.
        val options = DeepSeekChatOptions.builder().model("deepseek-flash").maxTokens(4096)

        assertThat(
            client(thinking = false)
                .prompt("hi")
                .options(options)
                .call()
                .content()
        ).isEqualTo("ok")

        val sent = bodies.single()
        assertThat(sent.get("thinking").get("type").asString()).isEqualTo("disabled")
        assertThat(sent.has("reasoning_effort")).isFalse()
    }

    @Test
    fun `fast client stays non-thinking when streaming`() {
        val options = DeepSeekChatOptions.builder().model("deepseek-flash").maxTokens(4096)

        client(thinking = false)
            .prompt("hi")
            .options(options)
            .stream()
            .content()
            .onErrorComplete()
            .blockLast()

        val sent = bodies.single()
        assertThat(sent.get("stream").asBoolean()).isTrue()
        assertThat(sent.get("thinking").get("type").asString()).isEqualTo("disabled")
        assertThat(sent.has("reasoning_effort")).isFalse()
    }

    companion object {
        private const val COMPLETION =
            """{"id":"c1","object":"chat.completion","created":1,"model":"deepseek-flash",""" +
                """"choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}],""" +
                """"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}"""
    }
}