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
 * Drives [MacroClient] against a [MockRestServiceServer] so the URI assembly and the bearer
 * header are pinned without standing up svc-data. Mirrors [AlphaVantageNewsClientTest].
 */
class MacroClientTest {
    private val tokenService = mock<TokenService> { on { bearerToken } doReturn BEARER }

    private fun clientWithServer(): Pair<MacroClient, MockRestServiceServer> {
        val builder = RestClient.builder().baseUrl(BASE_URL)
        val server = MockRestServiceServer.bindTo(builder).build()
        return MacroClient(builder.build(), tokenService) to server
    }

    @Test
    fun `getIndicators requests the indicators endpoint with lookbackDays and bearer header`() {
        val (client, server) = clientWithServer()
        server
            .expect(method(HttpMethod.GET))
            .andExpect(
                requestTo(allOf(containsString("/macro/indicators"), containsString("lookbackDays=14")))
            ).andExpect(header("Authorization", BEARER))
            .andRespond(withSuccess(INDICATORS_JSON, MediaType.APPLICATION_JSON))

        val result = client.getIndicators(14)

        assertThat(result["lookbackDays"]).isEqualTo(14)
        server.verify()
    }

    @Test
    fun `getIndicators returns an empty map when the response body is empty`() {
        val (client, server) = clientWithServer()
        server
            .expect(method(HttpMethod.GET))
            .andExpect(requestTo(containsString("/macro/indicators")))
            .andRespond(withSuccess())

        val result = client.getIndicators(14)

        assertThat(result).isEmpty()
        server.verify()
    }

    @Test
    fun `getRateExpectations requests the rate-expectations endpoint with bearer header`() {
        val (client, server) = clientWithServer()
        server
            .expect(method(HttpMethod.GET))
            .andExpect(requestTo(containsString("/macro/rate-expectations")))
            .andExpect(header("Authorization", BEARER))
            .andRespond(withSuccess(RATE_EXPECTATIONS_JSON, MediaType.APPLICATION_JSON))

        val result = client.getRateExpectations()

        assertThat(result).isNotNull
        assertThat(result?.get("event")).isEqualTo("KXFEDDECISION-26SEP")
        server.verify()
    }

    @Test
    fun `getRateExpectations returns null when the response body is empty`() {
        val (client, server) = clientWithServer()
        server
            .expect(method(HttpMethod.GET))
            .andExpect(requestTo(containsString("/macro/rate-expectations")))
            .andRespond(withSuccess())

        val result = client.getRateExpectations()

        assertThat(result).isNull()
        server.verify()
    }

    private companion object {
        const val BASE_URL = "http://bc-data"
        const val BEARER = "Bearer test"
        const val INDICATORS_JSON =
            """{"asOf":"2026-09-11","lookbackDays":14,"yields":[],"oil":[]}"""
        const val RATE_EXPECTATIONS_JSON =
            """{"event":"KXFEDDECISION-26SEP","title":"Fed decision","closeTime":"2026-09-26T18:00:00Z",""" +
                """"outcomes":[{"label":"Hike 25bps","probability":0.635,"volume24h":null}]}"""
    }
}