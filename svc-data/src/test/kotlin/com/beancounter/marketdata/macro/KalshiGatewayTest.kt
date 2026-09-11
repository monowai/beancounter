package com.beancounter.marketdata.macro

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

/**
 * Drives [KalshiGateway] against a [MockRestServiceServer] so the URI assembly and JSON tolerance
 * (unknown fields, null dollar fields) are pinned without hitting the real Kalshi API. Mirrors the
 * technique used by svc-agent's `AlphaVantageNewsClientTest`.
 */
class KalshiGatewayTest {
    private fun gatewayWithServer(): Pair<KalshiGateway, MockRestServiceServer> {
        val builder = RestClient.builder().baseUrl(BASE_URL)
        val server = MockRestServiceServer.bindTo(builder).build()
        return KalshiGateway(builder.build()) to server
    }

    @Test
    fun `getEvents requests the series ticker with open status`() {
        val (gateway, server) = gatewayWithServer()
        server
            .expect(method(HttpMethod.GET))
            .andExpect(
                requestTo(
                    allOf(
                        containsString("/trade-api/v2/events"),
                        containsString("series_ticker=KXFEDDECISION"),
                        containsString("status=open")
                    )
                )
            ).andRespond(withSuccess(EVENTS_JSON, MediaType.APPLICATION_JSON))

        val result = gateway.getEvents("KXFEDDECISION")

        assertThat(result.events).hasSize(1)
        assertThat(result.events.first().eventTicker).isEqualTo("KXFEDDECISION-26SEP")
        server.verify()
    }

    @Test
    fun `getMarkets requests the event ticker with open status`() {
        val (gateway, server) = gatewayWithServer()
        server
            .expect(method(HttpMethod.GET))
            .andExpect(
                requestTo(
                    allOf(
                        containsString("/trade-api/v2/markets"),
                        containsString("event_ticker=KXFEDDECISION-26SEP"),
                        containsString("status=open")
                    )
                )
            ).andRespond(withSuccess(MARKETS_JSON, MediaType.APPLICATION_JSON))

        val result = gateway.getMarkets("KXFEDDECISION-26SEP")

        assertThat(result.markets).hasSize(2)
        server.verify()
    }

    @Test
    fun `getEvents falls back to an empty response when the body deserializes to null`() {
        val (gateway, server) = gatewayWithServer()
        server
            .expect(method(HttpMethod.GET))
            .andExpect(requestTo(containsString("/trade-api/v2/events")))
            .andRespond(withSuccess("null", MediaType.APPLICATION_JSON))

        val result = gateway.getEvents("KXFEDDECISION")

        assertThat(result.events).isEmpty()
        server.verify()
    }

    @Test
    fun `getMarkets falls back to an empty response when the body deserializes to null`() {
        val (gateway, server) = gatewayWithServer()
        server
            .expect(method(HttpMethod.GET))
            .andExpect(requestTo(containsString("/trade-api/v2/markets")))
            .andRespond(withSuccess("null", MediaType.APPLICATION_JSON))

        val result = gateway.getMarkets("KXFEDDECISION-26SEP")

        assertThat(result.markets).isEmpty()
        server.verify()
    }

    @Test
    fun `unknown fields and null dollar fields do not break parsing`() {
        val (gateway, server) = gatewayWithServer()
        server
            .expect(method(HttpMethod.GET))
            .andExpect(requestTo(containsString("/trade-api/v2/markets")))
            .andRespond(withSuccess(MARKETS_WITH_NULLS_AND_EXTRA_FIELDS_JSON, MediaType.APPLICATION_JSON))

        val result = gateway.getMarkets("KXFEDDECISION-26SEP")

        val market = result.markets.first()
        assertThat(market.ticker).isEqualTo("KXFEDDECISION-26SEP-H25")
        assertThat(market.yesBidDollars).isNull()
        assertThat(market.yesAskDollars).isNull()
        assertThat(market.lastPriceDollars).isNull()
        server.verify()
    }

    private companion object {
        const val BASE_URL = "https://api.elections.kalshi.com"
        const val EVENTS_JSON =
            """{"events":[{"event_ticker":"KXFEDDECISION-26SEP","title":"Fed decision September 2026"}]}"""
        const val MARKETS_JSON =
            """
            {"markets":[
              {"ticker":"KXFEDDECISION-26SEP-H25","yes_sub_title":"Hike 25bps","close_time":"2026-09-16T17:59:00Z",
               "last_price_dollars":0.02,"yes_bid_dollars":"0.01","yes_ask_dollars":"0.03","volume_24h_fp":12345.6},
              {"ticker":"KXFEDDECISION-26SEP-C25","yes_sub_title":"Cut 25bps","close_time":"2026-09-16T17:59:00Z",
               "last_price_dollars":0.635,"yes_bid_dollars":"0.62","yes_ask_dollars":"0.65","volume_24h_fp":654442.24}
            ]}
            """
        const val MARKETS_WITH_NULLS_AND_EXTRA_FIELDS_JSON =
            """
            {"markets":[
              {"ticker":"KXFEDDECISION-26SEP-H25","yes_sub_title":"Hike 25bps","subtitle":"Hike 25bps",
               "close_time":"2026-09-16T17:59:00Z","last_price_dollars":null,"yes_bid_dollars":null,
               "yes_ask_dollars":null,"volume_24h_fp":null,"rules_primary":"some long rules text",
               "strike_type":"between","floor_strike":0.0}
            ]}
            """
    }
}