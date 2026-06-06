package com.beancounter.agent.clients

import com.beancounter.auth.TokenService
import org.assertj.core.api.Assertions.assertThat
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

    private companion object {
        const val BASE_URL = "http://bc-data"
        const val BEARER = "Bearer test"
        const val FEED_JSON = """{"feed":[{"title":"Tech Wreck"}],"count":1}"""
    }
}