package com.beancounter.agent.screenshot

import com.beancounter.agent.AgentModelTiers
import com.beancounter.agent.DeepSeekConnection
import com.beancounter.common.utils.BcJson
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

/**
 * The screenshot reader asks for the vision tier's model, not the chat model. A
 * gateway routes the two apart: its chat route drops images (waharoa#10).
 */
class ScreenshotConfigTest {
    private val requests = mutableListOf<String>()
    private val server =
        HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
            createContext("/chat/completions") { exchange ->
                requests += exchange.requestBody.readAllBytes().decodeToString()
                val reply = """{"choices":[{"message":{"content":"{\"documentType\":\"HOLDINGS\",\"rows\":[]}"}}]}"""
                exchange.sendResponseHeaders(200, reply.length.toLong())
                exchange.responseBody.use { it.write(reply.toByteArray()) }
            }
            start()
        }

    @AfterEach
    fun stop() = server.stop(0)

    @Test
    fun `should read a screenshot with the vision model`() {
        val connection =
            DeepSeekConnection(
                apiKey = "key",
                baseUrl = "http://localhost:${server.address.port}",
                model = "chat-model",
                temperature = 0.2,
                maxTokens = 4096
            )
        val reader =
            ScreenshotConfig().screenshotReader(
                connection,
                AgentModelTiers(fast = "chat-model", vision = "vision-model")
            )

        reader.read(Screenshot(byteArrayOf(1, 2, 3), "image/png"))

        assertThat(requests).hasSize(1)
        assertThat(BcJson.objectMapper.readTree(requests.single())["model"].asString()).isEqualTo("vision-model")
    }
}