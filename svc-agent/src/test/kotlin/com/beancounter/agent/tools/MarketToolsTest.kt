package com.beancounter.agent.tools

import com.beancounter.agent.client.WatchlistClient
import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.MarketService
import com.beancounter.client.services.PriceService
import com.beancounter.client.services.StaticService
import com.beancounter.common.contracts.PricePoint
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.ai.support.ToolCallbacks
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

    @Mock
    private lateinit var watchlistClient: WatchlistClient

    @Mock
    private lateinit var dateUtils: DateUtils

    private lateinit var marketTools: MarketTools

    @BeforeEach
    fun setup() {
        lenient().`when`(tokenService.bearerToken).thenReturn("test-token")
        lenient().`when`(dateUtils.date).thenReturn(today)
        marketTools =
            MarketTools(
                marketService,
                staticService,
                fxService,
                tokenService,
                priceService,
                watchlistClient,
                dateUtils
            )
    }

    private val today = LocalDate.of(2026, 10, 19)

    private fun lookupOn(market: String) = argThat<PriceRequest> { assets.single().market == market }

    private fun spot(
        code: String,
        market: String
    ) = MarketData(
        asset = Asset(code = code, id = "$code-id", market = Market(market)),
        priceDate = today,
        close = BigDecimal("700.00"),
        previousClose = BigDecimal("710.00"),
        change = BigDecimal("-10.00"),
        changePercent = BigDecimal("-0.0141")
    )

    @Test
    fun `getCurrentPrice falls back to the US listing when a US exchange code finds nothing`() {
        whenever(priceService.getPrices(lookupOn("NASDAQ"), any())).thenReturn(PriceResponse(emptyList()))
        whenever(priceService.getPrices(lookupOn("US"), any())).thenReturn(PriceResponse(listOf(spot("XYZ", "US"))))

        val result = marketTools.getCurrentPrice("NASDAQ", "XYZ")

        assertThat(result.market).isEqualTo("US")
        assertThat(result.assetCode).isEqualTo("XYZ")
        assertThat(result.priceClose).isEqualByComparingTo(BigDecimal("700.00"))
    }

    @Test
    fun `getCurrentPrice treats NYSE, AMEX and lower-case exchange codes as US listings`() {
        whenever(priceService.getPrices(lookupOn("US"), any())).thenReturn(PriceResponse(listOf(spot("XYZ", "US"))))
        listOf("NYSE", "amex", "Arca").forEach { exchange ->
            whenever(priceService.getPrices(lookupOn(exchange), any())).thenReturn(PriceResponse(emptyList()))

            assertThat(marketTools.getCurrentPrice(exchange, "XYZ").market).isEqualTo("US")
        }
    }

    @Test
    fun `getCurrentPrice reads a US listing directly with one lookup`() {
        whenever(priceService.getPrices(lookupOn("US"), any())).thenReturn(PriceResponse(listOf(spot("XYZ", "US"))))

        assertThat(marketTools.getCurrentPrice("US", "XYZ").priceClose).isEqualByComparingTo(BigDecimal("700.00"))
        verify(priceService, times(1)).getPrices(any(), any())
    }

    @Test
    fun `getCurrentPrice does not retry a non-US market and names the asset in the failure`() {
        whenever(priceService.getPrices(any(), any())).thenReturn(PriceResponse(emptyList()))

        assertThatThrownBy { marketTools.getCurrentPrice("ASX", "NOPE") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("ASX:NOPE")
        verify(priceService, times(1)).getPrices(any(), any())
        verify(priceService, never()).getPrices(lookupOn("US"), any())
    }

    // One close a day, rising from 100, back far enough to cover the 52-week window.
    private fun history(days: Int): List<PricePoint> =
        (days - 1 downTo 0).mapIndexed { i, back ->
            PricePoint(priceDate = today.minusDays(back.toLong()), close = BigDecimal(100 + i))
        }

    @Test
    fun `getPriceMomentum resolves a NASDAQ code to the US listing and reads thirteen months of closes`() {
        whenever(priceService.getPrices(lookupOn("NASDAQ"), any())).thenReturn(PriceResponse(emptyList()))
        whenever(priceService.getPrices(lookupOn("US"), any())).thenReturn(PriceResponse(listOf(spot("XYZ", "US"))))
        // Thirteen months and a week back from 2026-10-19.
        whenever(watchlistClient.getCloses("XYZ-id", "2025-09-12")).thenReturn(history(400))

        val result = marketTools.getPriceMomentum("NASDAQ", "XYZ")

        assertThat(result.assetCode).isEqualTo("XYZ")
        assertThat(result.market).isEqualTo("US")
        assertThat(result.latestClose).isEqualTo(499.0)
        assertThat(result.latestCloseDate).isEqualTo("2026-10-19")
        assertThat(result.momentum.high52w).isEqualTo(499.0)
        assertThat(result.momentum.low52w).isEqualTo(135.0)
        assertThat(result.momentum.fromHigh52w).isEqualTo(0.0)
        assertThat(result.momentum.return12m).isNotNull()
    }

    @Test
    fun `getPriceMomentum reports a drawdown from the 52 week high`() {
        whenever(priceService.getPrices(lookupOn("US"), any())).thenReturn(PriceResponse(listOf(spot("XYZ", "US"))))
        val peaked =
            history(400).map {
                if (it.priceDate == today.minusDays(30)) it.copy(close = BigDecimal(998)) else it
            }
        whenever(watchlistClient.getCloses(any(), any())).thenReturn(peaked)

        val momentum = marketTools.getPriceMomentum("US", "XYZ").momentum

        assertThat(momentum.high52w).isEqualTo(998.0)
        assertThat(momentum.fromHigh52w).isEqualTo(-0.5)
    }

    @Test
    fun `getPriceMomentum leaves the readings empty when there is no history`() {
        whenever(priceService.getPrices(lookupOn("US"), any())).thenReturn(PriceResponse(listOf(spot("XYZ", "US"))))
        whenever(watchlistClient.getCloses(any(), any())).thenReturn(emptyList())

        val result = marketTools.getPriceMomentum("US", "XYZ")

        assertThat(result.latestClose).isNull()
        assertThat(result.momentum.fromHigh52w).isNull()
    }

    @Test
    fun `getPriceMomentum fails clearly for an unknown asset without reading history`() {
        whenever(priceService.getPrices(any(), any())).thenReturn(PriceResponse(emptyList()))

        assertThatThrownBy { marketTools.getPriceMomentum("ASX", "NOPE") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("ASX:NOPE")
        verify(watchlistClient, never()).getCloses(any(), any())
    }

    @Test
    fun `price momentum is registered as a tool that owns 52 week highs and drawdowns`() {
        val callbacks = ToolCallbacks.from(marketTools).associateBy { it.toolDefinition.name() }

        assertThat(callbacks).containsKeys("getCurrentPrice", "getPriceMomentum")
        assertThat(callbacks.getValue("getPriceMomentum").toolDefinition.description())
            .contains("52-week high")
            .contains("drawdown")
            .contains("Never say")
        assertThat(callbacks.getValue("getCurrentPrice").toolDefinition.description())
            .contains("getPriceMomentum")
        assertThat(callbacks.getValue("getCurrentPrice").toolDefinition.inputSchema())
            .contains("US listings use US")
            .doesNotContain("NASDAQ, NZX")
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