package com.beancounter.agent.clients

import com.beancounter.auth.TokenService
import com.beancounter.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

/**
 * Drives [AlphaVantageNewsClient] against a [MockRestServiceServer] so the URI assembly and the
 * bearer header are pinned without standing up svc-data.
 */
class AlphaVantageNewsClientTest {
    private val tokenService = mock<TokenService> { on { bearerToken } doReturn BEARER }

    private fun clientWithServer(): Pair<AlphaVantageNewsClient, MockRestServiceServer> {
        val builder = RestClient.builder().baseUrl(BASE_URL)
        val server = MockRestServiceServer.bindTo(builder).build()
        return AlphaVantageNewsClient(builder.build(), tokenService) to server
    }

    @Test
    fun `getMarketNews requests the market endpoint with comma-joined proxy symbols`() {
        val (client, server) = clientWithServer()
        server
            .expect(method(HttpMethod.GET))
            .andExpect(
                requestTo(
                    allOf(containsString("/news/market"), containsString("GSPC.INDX"), containsString("DJI.INDX"))
                )
            ).andExpect(header("Authorization", BEARER))
            .andRespond(withSuccess(FEED_JSON, MediaType.APPLICATION_JSON))

        val result = client.getMarketNews(listOf("GSPC.INDX", "DJI.INDX"))

        assertThat(result["count"]).isEqualTo(1)
        server.verify()
    }

    @Test
    fun `getTopicNews requests the topic endpoint with comma-joined topics, URL-encoding spaces`() {
        val (client, server) = clientWithServer()
        server
            .expect(method(HttpMethod.GET))
            .andExpect(
                requestTo(
                    allOf(
                        containsString("/news/topic"),
                        containsString("stock%20markets"),
                        containsString("economy")
                    )
                )
            ).andExpect(header("Authorization", BEARER))
            .andRespond(withSuccess(FEED_JSON, MediaType.APPLICATION_JSON))

        val result = client.getTopicNews(listOf("stock markets", "economy"))

        assertThat(result["count"]).isEqualTo(1)
        server.verify()
    }

    @Test
    fun `getNewsSentiment passes tickers, market and topics through to the news endpoint`() {
        val (client, server) = clientWithServer()
        server
            .expect(method(HttpMethod.GET))
            .andExpect(
                requestTo(
                    allOf(
                        containsString("/news?"),
                        containsString("AAPL"),
                        containsString("US"),
                        containsString("earnings")
                    )
                )
            ).andExpect(header("Authorization", BEARER))
            .andRespond(withSuccess(FEED_JSON, MediaType.APPLICATION_JSON))

        val result = client.getNewsSentiment("AAPL", market = "US", topics = "earnings")

        assertThat(result["count"]).isEqualTo(1)
        server.verify()
    }

    @Test
    fun `getMarketSentiment requests the market sentiment endpoint for the given days`() {
        val (client, server) = clientWithServer()
        server
            .expect(method(HttpMethod.GET))
            .andExpect(requestTo("$BASE_URL/news/sentiment/market?days=14"))
            .andExpect(header("Authorization", BEARER))
            .andRespond(
                withSuccess(
                    """{"data":[{"kind":"TOPIC","name":"inflation","points":[]}]}""",
                    MediaType.APPLICATION_JSON
                )
            )

        val result = client.getMarketSentiment(14)

        assertThat(result["data"] as List<*>).hasSize(1)
        server.verify()
    }

    @Test
    fun `getAssetSentiment requests the sentiment endpoint with comma-joined asset ids`() {
        val (client, server) = clientWithServer()
        server
            .expect(method(HttpMethod.GET))
            .andExpect(requestTo("$BASE_URL/news/sentiment?assetIds=a%2Cb&days=14"))
            .andExpect(header("Authorization", BEARER))
            .andRespond(withSuccess("""{"data":{"a":[]}}""", MediaType.APPLICATION_JSON))

        val result = client.getAssetSentiment(listOf("a", "b"), 14)

        assertThat(result["data"].toString()).contains("a=")
        server.verify()
    }

    @Test
    fun `getMarketSentiment throws when the response body is missing`() {
        val (client, server) = clientWithServer()
        server
            .expect(method(HttpMethod.GET))
            .andExpect(requestTo("$BASE_URL/news/sentiment/market?days=14"))
            .andRespond(withSuccess())

        assertThatThrownBy { client.getMarketSentiment(14) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("market news sentiment")
        server.verify()
    }

    @Test
    fun `getAssetSentiment throws when the response body is missing`() {
        val (client, server) = clientWithServer()
        server
            .expect(method(HttpMethod.GET))
            .andExpect(requestTo("$BASE_URL/news/sentiment?assetIds=a&days=14"))
            .andRespond(withSuccess())

        assertThatThrownBy { client.getAssetSentiment(listOf("a"), 14) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("asset news sentiment")
        server.verify()
    }

    private companion object {
        const val BASE_URL = "http://bc-data"
        const val BEARER = "Bearer test"
        const val FEED_JSON = """{"feed":[{"title":"Tech Wreck"}],"count":1}"""
    }
}