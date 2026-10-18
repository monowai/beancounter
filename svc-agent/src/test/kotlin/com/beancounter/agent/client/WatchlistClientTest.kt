package com.beancounter.agent.client

import com.beancounter.auth.TokenService
import com.beancounter.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.math.BigDecimal

/**
 * Reads the caller's watchlist and an asset's price history from svc-data, in the shapes
 * svc-data serialises.
 */
class WatchlistClientTest {
    private val tokenService = mock<TokenService> { on { bearerToken } doReturn "Bearer test" }
    private val builder = RestClient.builder().baseUrl("http://bc-data/api")
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

    private companion object {
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
            "changeSinceAdded":29.05,"changeSinceAddedPercent":0.0581,"toTarget":0.134108},
            {"id":"w2","asset":{"code":"NEW","id":"a2","name":"","market":{"code":"NZX","name":"NZX","currencyId":"NZD"},
            "assetCategory":{"id":"ETF","name":"Exchange Traded Fund"},"status":"Active"},
            "addedPrice":1,"addedOn":"2026-10-18","targetPrice":null,"currency":{"code":"NZD","name":"Dollar","symbol":"${'$'}"},
            "price":null,"priceDate":null,"changeOnDay":null,"changeOnDayPercent":null,
            "changeSinceAdded":null,"changeSinceAddedPercent":null,"toTarget":null}
            ]}}
            """.trimIndent()

        val HISTORY =
            """
            {"asset":{"code":"MSFT","id":"a1","name":"Microsoft Corporation","market":{"code":"US"}},
            "prices":[{"priceDate":"2026-10-15","close":522.61},{"priceDate":"2026-10-16","close":529.05}]}
            """.trimIndent()
    }
}