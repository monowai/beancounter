package com.beancounter.agent.tools

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.MarketService
import com.beancounter.client.services.PriceService
import com.beancounter.client.services.StaticService
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class MarketToolsTest {
    @Mock
    private lateinit var marketService: MarketService

    @Mock
    private lateinit var staticService: StaticService

    @Mock
    private lateinit var fxService: FxService

    @Mock
    private lateinit var tokenService: TokenService

    @Mock
    private lateinit var priceService: PriceService

    private lateinit var marketTools: MarketTools

    @BeforeEach
    fun setup() {
        lenient().`when`(tokenService.bearerToken).thenReturn("test-token")
        marketTools = MarketTools(marketService, staticService, fxService, tokenService, priceService)
    }

    @Test
    fun `getCurrentPrice returns close and changePercent for asset`() {
        val asset = Asset(code = "AAPL", id = "aapl-id", market = Market("NASDAQ"))
        val md =
            MarketData(
                asset = asset,
                priceDate = LocalDate.of(2026, 5, 7),
                close = BigDecimal("180.50"),
                previousClose = BigDecimal("178.00"),
                change = BigDecimal("2.50"),
                changePercent = BigDecimal("0.014045")
            )
        whenever(priceService.getPrices(any(), any())).thenReturn(PriceResponse(listOf(md)))

        val result = marketTools.getCurrentPrice("NASDAQ", "AAPL")

        assertThat(result.assetCode).isEqualTo("AAPL")
        assertThat(result.market).isEqualTo("NASDAQ")
        assertThat(result.priceClose).isEqualByComparingTo(BigDecimal("180.50"))
        assertThat(result.changePercent).isEqualByComparingTo(BigDecimal("0.014045"))
        assertThat(result.priceDate).isEqualTo("2026-05-07")
    }

    @Test
    fun `getCurrentPrice throws when no price data returned`() {
        whenever(priceService.getPrices(any(), any())).thenReturn(PriceResponse(emptyList()))

        assertThatThrownBy { marketTools.getCurrentPrice("NASDAQ", "GHOST") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("GHOST")
    }

    private fun priceOf(
        code: String,
        market: String,
        changePercent: String
    ): MarketData =
        MarketData(
            asset = Asset(code = code, id = "$code-id", market = Market(market)),
            priceDate = LocalDate.of(2026, 6, 7),
            close = BigDecimal("100.00"),
            previousClose = BigDecimal("103.00"),
            change = BigDecimal("-3.00"),
            changePercent = BigDecimal(changePercent)
        )

    @Test
    fun `getBenchmark market scope returns the broad index changes`() {
        whenever(priceService.getPrices(any(), any())).thenReturn(
            PriceResponse(
                listOf(
                    priceOf("GSPC", "INDEX", "-0.0264"),
                    priceOf("IXIC", "INDEX", "-0.0410"),
                    priceOf("DJI", "INDEX", "-0.0180")
                )
            )
        )

        @Suppress("UNCHECKED_CAST")
        val benchmarks = marketTools.getBenchmark("market")["benchmarks"] as List<Map<String, Any>>

        assertThat(benchmarks).hasSize(3)
        assertThat(benchmarks.map { it["name"] })
            .containsExactly("S&P 500", "Nasdaq Composite", "Dow Jones Industrial Average")
        assertThat(benchmarks.first()["changePercent"]).isEqualTo(BigDecimal("-0.0264"))
    }

    @Test
    fun `getBenchmark sector scope maps to its SPDR ETF`() {
        whenever(priceService.getPrices(any(), any()))
            .thenReturn(PriceResponse(listOf(priceOf("XLK", "US", "-0.031"))))

        @Suppress("UNCHECKED_CAST")
        val benchmarks = marketTools.getBenchmark("Technology")["benchmarks"] as List<Map<String, Any>>

        assertThat(benchmarks).hasSize(1)
        assertThat(benchmarks.first()["code"]).isEqualTo("XLK")
        assertThat(benchmarks.first()["name"]).isEqualTo("Technology (XLK)")
    }

    @Test
    fun `getBenchmark unknown scope returns supported list without pricing`() {
        val result = marketTools.getBenchmark("crypto")

        assertThat(result["status"]).isEqualTo("unknown_scope")
        @Suppress("UNCHECKED_CAST")
        val supported = result["supportedScopes"] as List<String>
        assertThat(supported).contains("market", "technology", "energy")
        org.mockito.kotlin.verifyNoInteractions(priceService)
    }

    @Test
    fun `getBenchmark returns no_coverage when no prices come back`() {
        whenever(priceService.getPrices(any(), any())).thenReturn(PriceResponse(emptyList()))

        assertThat(marketTools.getBenchmark("market")["status"]).isEqualTo("no_coverage")
    }
}