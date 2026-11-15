package com.beancounter.agent.screenshot

import com.beancounter.common.utils.BcJson
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.core.JacksonException
import java.util.Base64

/**
 * DeepSeek's chat completions endpoint as a [VisionModel]. Spring AI's DeepSeek
 * module sends text only, so the image goes as an OpenAI-style `image_url` part.
 */
class DeepSeekVision(
    private val http: RestClient,
    private val apiKey: String,
    private val model: String
) : VisionModel {
    override fun read(
        instruction: String,
        screenshot: Screenshot
    ): String {
        val body =
            try {
                http
                    .post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BcJson.objectMapper.writeValueAsString(request(instruction, screenshot)))
                    .retrieve()
                    .body(String::class.java)
            } catch (e: RestClientException) {
                throw VisionUnavailableException("The vision model did not answer", e)
            }
        return content(body) ?: throw VisionUnavailableException("The vision model gave no answer")
    }

    private fun request(
        instruction: String,
        screenshot: Screenshot
    ): Map<String, Any> {
        val image = "data:${screenshot.mediaType};base64,${Base64.getEncoder().encodeToString(screenshot.bytes)}"
        return mapOf(
            "model" to model,
            "temperature" to 0,
            "max_tokens" to MAX_TOKENS,
            "response_format" to mapOf("type" to "json_object"),
            "messages" to
                listOf(
                    mapOf(
                        "role" to "user",
                        "content" to
                            listOf(
                                mapOf("type" to "text", "text" to instruction),
                                mapOf("type" to "image_url", "image_url" to mapOf("url" to image))
                            )
                    )
                )
        )
    }

    private fun content(body: String?): String? =
        try {
            body
                ?.let { BcJson.objectMapper.readValue(it, Completion::class.java) }
                ?.choices
                ?.firstOrNull()
                ?.message
                ?.content
        } catch (e: JacksonException) {
            throw VisionUnavailableException("The vision model's answer was not understood", e)
        }

    private data class Completion(
        val choices: List<Choice> = emptyList()
    )

    private data class Choice(
        val message: Message? = null
    )

    private data class Message(
        val content: String? = null
    )

    private companion object {
        const val MAX_TOKENS = 8192
    }
}