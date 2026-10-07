package com.beancounter.marketdata.macro

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

/**
 * Drives [FredGateway] against a [MockRestServiceServer] so the keyless `fredgraph.csv` URI is
 * pinned without hitting FRED. Mirrors [KalshiGatewayTest].
 */
class FredGatewayTest {
    private fun gatewayWithServer(): Pair<FredGateway, MockRestServiceServer> {
        val builder = RestClient.builder().baseUrl(BASE_URL)
        val server = MockRestServiceServer.bindTo(builder).build()
        return FredGateway(builder.build()) to server
    }

    @Test
    fun `getSeriesCsv requests the fredgraph csv for the series id`() {
        val (gateway, server) = gatewayWithServer()
        server
            .expect(method(HttpMethod.GET))
            .andExpect(requestTo("$BASE_URL/graph/fredgraph.csv?id=DGS10"))
            .andRespond(withSuccess(CSV, MediaType.TEXT_PLAIN))

        val body = gateway.getSeriesCsv("DGS10")

        assertThat(body).isEqualTo(CSV)
        server.verify()
    }

    @Test
    fun `getSeriesCsv returns an empty string when the body is empty`() {
        val (gateway, server) = gatewayWithServer()
        server
            .expect(method(HttpMethod.GET))
            .andExpect(requestTo("$BASE_URL/graph/fredgraph.csv?id=DGS2"))
            .andRespond(withSuccess())

        assertThat(gateway.getSeriesCsv("DGS2")).isEmpty()
        server.verify()
    }

    private companion object {
        const val BASE_URL = "https://fred.stlouisfed.org"
        const val CSV = "observation_date,DGS10\n2026-10-03,4.12\n"
    }
}