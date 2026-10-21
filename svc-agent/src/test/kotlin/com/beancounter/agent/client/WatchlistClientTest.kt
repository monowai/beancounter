package com.beancounter.agent.client

import com.beancounter.auth.TokenService
import com.beancounter.common.client.RestClientErrorHandler
import com.beancounter.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Reads the caller's watchlist and an asset's price history from svc-data, in the shapes
 * svc-data serialises.
 */
class WatchlistClientTest {
    private val tokenService = mock<TokenService> { on { bearerToken } doReturn "Bearer test" }
    private val builder =
        RestClient.builder().baseUrl("http://bc-data/api").defaultStatusHandler(RestClientErrorHandler())
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client by lazy { WatchlistClient(builder.build(), tokenService) }

    @Test
    fun `should read the watchlist with each item's price and changes`() {
        server
            .expect(requestTo("http://bc-data/api/watchlist"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer test"))
            .andRespond(withSuccess(WATCHLIST, MediaType.APPLICATION_JSON))

        val items = client.getWatchlist()

        assertThat(items).hasSize(2)
        val priced = items.first()
        assertThat(priced.asset.code).isEqualTo("MSFT")
        assertThat(priced.asset.market.code).isEqualTo("US")
        assertThat(priced.currency.code).isEqualTo("USD")
        assertThat(priced.addedOn).isEqualTo("2026-10-01")
        assertThat(priced.price).isEqualByComparingTo("529.05")
        assertThat(priced.changeOnDayPercent).isEqualByComparingTo("0.012323")
        assertThat(priced.toTarget).isEqualByComparingTo("0.134108")
        val unpriced = items.last()
        assertThat(unpriced.price).isNull()
        assertThat(unpriced.targetPrice).isNull()
    }

    @Test
    fun `should fail rather than report an empty watchlist when svc-data returns no body`() {
        server.expect(requestTo("http://bc-data/api/watchlist")).andRespond(withNoContent())

        assertThatThrownBy { client.getWatchlist() }.isInstanceOf(BusinessException::class.java)
    }

    @Test
    fun `should read an asset's closes from the requested date`() {
        server
            .expect(requestTo("http://bc-data/api/prices/a1/history?from=2025-09-16"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(HISTORY, MediaType.APPLICATION_JSON))

        val closes = client.getCloses("a1", "2025-09-16")

        assertThat(closes.map { it.priceDate.toString() to it.close })
            .containsExactly("2026-10-15" to BigDecimal("522.61"), "2026-10-16" to BigDecimal("529.05"))
    }

    @Test
    fun `should read an asset's SEC fundamentals snapshot`() {
        server
            .expect(requestTo("http://bc-data/api/assets/a1/fundamentals"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer test"))
            .andRespond(withSuccess(FUNDAMENTALS, MediaType.APPLICATION_JSON))

        val fundamentals = client.getFundamentals("a1")

        assertThat(fundamentals).isNotNull
        assertThat(fundamentals!!.fiscalYear).isEqualTo(2026)
        assertThat(fundamentals.fiscalYearEnd).isEqualTo(LocalDate.parse("2026-06-30"))
        assertThat(fundamentals.epsDiluted).isEqualByComparingTo("13.64")
        assertThat(fundamentals.revenue).isEqualByComparingTo("281724000000")
        assertThat(fundamentals.dividendsPerShare).isNull()
        assertThat(fundamentals.sharesOutstanding).isEqualTo(7_433_000_000L)
    }

    @Test
    fun `should report no fundamentals when svc-data has no snapshot`() {
        server
            .expect(requestTo("http://bc-data/api/assets/a2/fundamentals"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertThat(client.getFundamentals("a2")).isNull()
    }

    private companion object {
        val FUNDAMENTALS =
            """
            {"assetId":"a1","source":"SEC","fiscalYearEnd":"2026-06-30","fiscalYear":2026,
            "epsDiluted":13.64,"revenue":281724000000.00,"netIncome":101832000000.00,
            "dividendsPerShare":null,"sharesOutstanding":7433000000,"asOf":"2026-10-12"}
            """.trimIndent()

        val WATCHLIST =
            """
            {"data":{"items":[
            {"id":"w1","asset":{"code":"MSFT","id":"a1","name":"Microsoft Corporation",
            "market":{"code":"US","name":"US Market","currencyId":"USD",
            "currency":{"code":"USD","name":"Dollar","symbol":"${'$'}"}},
            "priceSymbol":"MSFT","accountingType":null,"assetCategory":{"id":"EQUITY","name":"Equity"},
            "systemUser":null,"status":"Active"},
            "addedPrice":500.00,"addedOn":"2026-10-01","targetPrice":600,
            "currency":{"code":"USD","name":"Dollar","symbol":"${'$'}"},
            "price":529.05,"priceDate":"2026-10-16","changeOnDay":6.44,"changeOnDayPercent":0.012323,
            "changeSinceAdded":29.05,"changeSinceAddedPercent":0.0581,"toTarget":0.134108,"targetHitOn":"2026-10-16"},
            {"id":"w2","asset":{"code":"NEW","id":"a2","name":"","market":{"code":"NZX","name":"NZX","currencyId":"NZD"},
            "assetCategory":{"id":"ETF","name":"Exchange Traded Fund"},"status":"Active"},
            "addedPrice":1,"addedOn":"2026-10-18","targetPrice":null,"currency":{"code":"NZD","name":"Dollar","symbol":"${'$'}"},
            "price":null,"priceDate":null,"changeOnDay":null,"changeOnDayPercent":null,
            "changeSinceAdded":null,"changeSinceAddedPercent":null,"toTarget":null,"targetHitOn":null}
            ]}}
            """.trimIndent()

        val HISTORY =
            """
            {"asset":{"code":"MSFT","id":"a1","name":"Microsoft Corporation","market":{"code":"US"}},
            "prices":[{"priceDate":"2026-10-15","close":522.61},{"priceDate":"2026-10-16","close":529.05}]}
            """.trimIndent()
    }
}