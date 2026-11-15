package com.beancounter.agent.screenshot

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

/** The request DeepSeek's chat completions endpoint receives for a screenshot, and what is read back. */
class DeepSeekVisionTest {
    private val builder = RestClient.builder().baseUrl("https://llm.test")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val vision = DeepSeekVision(builder.build(), apiKey = "secret-key", model = "vision-model")
    private val screenshot = Screenshot(byteArrayOf(10, 20, 30), "image/jpeg")

    @Test
    fun `should send the instruction and the image and return the model's text`() {
        server
            .expect(requestTo("https://llm.test/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer secret-key"))
            .andExpect(jsonPath("$.model").value("vision-model"))
            .andExpect(jsonPath("$.response_format.type").value("json_object"))
            .andExpect(jsonPath("$.messages.length()").value(1))
            .andExpect(jsonPath("$.messages[0].role").value("user"))
            .andExpect(jsonPath("$.messages[0].content[0].type").value("text"))
            .andExpect(jsonPath("$.messages[0].content[0].text").value("read this"))
            .andExpect(jsonPath("$.messages[0].content[1].type").value("image_url"))
            .andExpect(jsonPath("$.messages[0].content[1].image_url.url").value("data:image/jpeg;base64,ChQe"))
            .andRespond(
                withSuccess(
                    """{"id":"x","choices":[{"index":0,"message":{"role":"assistant","content":"{\"rows\":[]}"}}],"usage":{"total_tokens":9}}""",
                    MediaType.APPLICATION_JSON
                )
            )

        assertThat(vision.read("read this", screenshot)).isEqualTo("""{"rows":[]}""")
        server.verify()
    }

    @Test
    fun `should report a model that fails or answers nothing as unavailable`() {
        server.expect(requestTo("https://llm.test/chat/completions")).andRespond(withStatus(HttpStatus.BAD_GATEWAY))
        assertThatThrownBy { vision.read("read this", screenshot) }.isInstanceOf(VisionUnavailableException::class.java)

        server.reset()
        server
            .expect(requestTo("https://llm.test/chat/completions"))
            .andRespond(withSuccess("""{"choices":[]}""", MediaType.APPLICATION_JSON))
        assertThatThrownBy { vision.read("read this", screenshot) }.isInstanceOf(VisionUnavailableException::class.java)
    }
}