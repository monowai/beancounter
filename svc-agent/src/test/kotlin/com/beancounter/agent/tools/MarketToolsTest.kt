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
}