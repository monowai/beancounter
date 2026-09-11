package com.beancounter.marketdata.news

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset.offset
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

/**
 * Drives [HttpNewsEmbedder] against a [MockRestServiceServer] so the TEI `/embed` request shape,
 * batch chunking, client-side unit-normalization, and graceful-degradation-on-failure are pinned
 * without standing up a real bc-embed deployment.
 */
internal class HttpNewsEmbedderTest {
    private fun embedderWithServer(batchSize: Int = 32): Pair<HttpNewsEmbedder, MockRestServiceServer> {
        val builder = RestClient.builder().baseUrl(BASE_URL)
        val server = MockRestServiceServer.bindTo(builder).build()
        val properties = NewsEmbeddingProperties(enabled = true, url = BASE_URL, batchSize = batchSize)
        return HttpNewsEmbedder(builder.build(), properties) to server
    }

    @Test
    fun `posts inputs to the embed endpoint and returns unit-normalized vectors`() {
        val (embedder, server) = embedderWithServer()
        server
            .expect(method(HttpMethod.POST))
            .andExpect(requestTo("$BASE_URL/embed"))
            .andExpect(jsonPath("$.inputs[0]").value("hello"))
            .andRespond(withSuccess("""[[3.0, 4.0]]""", MediaType.APPLICATION_JSON))

        val result = embedder.embed(listOf("hello"))

        assertThat(result).hasSize(1)
        assertThat(result[0][0]).isCloseTo(0.6f, offset(0.0001f))
        assertThat(result[0][1]).isCloseTo(0.8f, offset(0.0001f))
        server.verify()
    }

    @Test
    fun `chunks requests at batchSize`() {
        val (embedder, server) = embedderWithServer(batchSize = 2)
        server
            .expect(method(HttpMethod.POST))
            .andExpect(requestTo("$BASE_URL/embed"))
            .andExpect(jsonPath("$.inputs[0]").value("a"))
            .andExpect(jsonPath("$.inputs[1]").value("b"))
            .andRespond(withSuccess("""[[1.0,0.0],[0.0,1.0]]""", MediaType.APPLICATION_JSON))
        server
            .expect(method(HttpMethod.POST))
            .andExpect(requestTo("$BASE_URL/embed"))
            .andExpect(jsonPath("$.inputs[0]").value("c"))
            .andRespond(withSuccess("""[[1.0,0.0]]""", MediaType.APPLICATION_JSON))

        val result = embedder.embed(listOf("a", "b", "c"))

        assertThat(result).hasSize(3)
        server.verify()
    }

    @Test
    fun `an empty text list never calls the endpoint`() {
        val (embedder, server) = embedderWithServer()

        val result = embedder.embed(emptyList())

        assertThat(result).isEmpty()
        server.verify()
    }

    @Test
    fun `transport error degrades to an empty list without throwing`() {
        val (embedder, server) = embedderWithServer()
        server
            .expect(method(HttpMethod.POST))
            .andExpect(requestTo("$BASE_URL/embed"))
            .andRespond(withServerError())

        val result = embedder.embed(listOf("hello"))

        assertThat(result).isEmpty()
        server.verify()
    }

    @Test
    fun `malformed response body degrades to an empty list without throwing`() {
        val (embedder, server) = embedderWithServer()
        server
            .expect(method(HttpMethod.POST))
            .andExpect(requestTo("$BASE_URL/embed"))
            .andRespond(withSuccess("""not-json""", MediaType.APPLICATION_JSON))

        val result = embedder.embed(listOf("hello"))

        assertThat(result).isEmpty()
        server.verify()
    }

    @Test
    fun `modelId and active come from properties`() {
        val (embedder, _) = embedderWithServer()

        assertThat(embedder.modelId).isEqualTo("all-minilm-l6-v2")
        assertThat(embedder.active).isTrue()
    }

    private companion object {
        const val BASE_URL = "http://bc-embed"
    }
}