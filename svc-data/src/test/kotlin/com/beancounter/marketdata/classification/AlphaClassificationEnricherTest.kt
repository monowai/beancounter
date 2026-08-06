package com.beancounter.marketdata.classification

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.beancounter.common.model.Asset
import com.beancounter.common.model.ClassificationItem
import com.beancounter.common.model.ClassificationLevel
import com.beancounter.common.model.ClassificationStandard
import com.beancounter.common.model.Market
import com.beancounter.common.model.Status
import com.beancounter.marketdata.providers.alpha.AlphaConfig
import com.beancounter.marketdata.providers.alpha.AlphaProxy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory

/**
 * Unit tests for [AlphaClassificationEnricher].
 *
 * Regression coverage for two production bugs found on kauri (bc-claude
 * fix/classification-refresh-resume):
 *
 * - Bug A: AlphaVantage's ETF_PROFILE returns placeholder holding symbols (literal `"n/a"`) for
 *   unlisted positions. Two such rows for the same parent asset violate the `asset_holding`
 *   unique constraint (`parent_asset_id`, `symbol`) and roll back the whole transaction,
 *   including the sector exposures that were about to be written in the same call.
 * - Bug B: a free-tier rate-limit response is HTTP 200 with an `Information`/`Note` body. The
 *   old code treated that identically to "nothing to classify" (`false`, no error counted),
 *   so a refresh run silently no-op'd on the bulk of its candidates.
 *
 * Uses a real [AlphaConfig] (its Jackson mapper is what production wires) with
 * `Asset.priceSymbol` set directly so `getPriceCode` returns a fixed value without needing
 * `AlphaConfig.markets` / market-code translation.
 */
class AlphaClassificationEnricherTest {
    private val alphaConfig = AlphaConfig()
    private lateinit var alphaProxy: AlphaProxy
    private lateinit var classificationService: ClassificationService
    private lateinit var enricher: AlphaClassificationEnricher

    private val market = Market(code = "NASDAQ", name = "NASDAQ")
    private val standard =
        ClassificationStandard(
            id = "std-alpha",
            key = ClassificationStandard.ALPHA,
            name = "AlphaVantage Sector Classification",
            provider = ClassificationStandard.PROVIDER_ALPHA
        )

    private fun item(
        code: String,
        level: ClassificationLevel
    ) = ClassificationItem(id = "item-$code", standard = standard, level = level, code = code, name = code)

    @BeforeEach
    fun setUp() {
        alphaProxy = mock()
        classificationService = mock()
        enricher = AlphaClassificationEnricher(alphaConfig, alphaProxy, classificationService)

        // apiKey field is set via @Value; not injected outside a Spring context, so it stays the
        // Kotlin default-initialized empty lateinit - fine, alphaProxy is mocked and does not
        // care what key it receives. Force-set to keep the mock call signatures obvious.
        setApiKey(enricher, "demo")

        whenever(classificationService.getAlphaStandard()).thenReturn(standard)
        whenever(
            classificationService.getOrCreateItem(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        ).thenAnswer { item("X", it.getArgument(1)) }
    }

    private fun setApiKey(
        target: AlphaClassificationEnricher,
        key: String
    ) {
        val field = AlphaClassificationEnricher::class.java.getDeclaredField("apiKey")
        field.isAccessible = true
        field.set(target, key)
    }

    private fun etf(symbol: String = "VOO") =
        Asset(
            id = "etf-1",
            code = symbol,
            name = "ETF",
            category = "ETF",
            market = market,
            priceSymbol = symbol,
            status = Status.Active
        )

    private fun equity(symbol: String = "AAPL") =
        Asset(
            id = "eq-1",
            code = symbol,
            name = "Equity",
            category = "EQUITY",
            market = market,
            priceSymbol = symbol,
            status = Status.Active
        )

    /**
     * Providers routinely type exchange-traded funds as `MUTUAL FUND`. kauri categorises VanEck's
     * US-listed SMOT that way — a NASDAQ ETF whose sector weights AlphaVantage serves today — so
     * treating the category as non-fund meant it was never asked about at all.
     */
    @Test
    fun `a MUTUAL FUND is treated as fund-like and enriched from the ETF profile`() {
        val fund =
            Asset(
                id = "etf-1",
                code = "SMOT",
                name = "VANECK MORNINGSTAR SMID MOAT",
                category = "MUTUAL FUND",
                market = market,
                priceSymbol = "SMOT",
                status = Status.Active
            )
        val body = """{"sectors": [{"sector": "Health Care", "weight": "0.18"}]}"""
        whenever(alphaProxy.getEtfProfile(eq("SMOT"), any())).thenReturn(body)

        assertThat(enricher.canEnrich(fund)).isTrue()
        assertThat(enricher.isEtf(fund)).isTrue()

        val result = enricher.enrichClassification(fund)

        assertThat(result).isEqualTo(EnrichmentResult.ENRICHED)
        verify(classificationService).addExposure(any(), any(), any(), any(), any())
    }

    @Test
    fun `enrichEtf skips placeholder and duplicate holding symbols but still writes sectors`() {
        val body =
            """
            {
              "sectors": [
                {"sector": "Technology", "weight": "0.35"},
                {"sector": "Healthcare", "weight": "0.15"}
              ],
              "holdings": [
                {"symbol": "AAPL", "description": "Apple Inc", "weight": "0.07"},
                {"symbol": "n/a", "description": "Unlisted", "weight": "0.02"},
                {"symbol": "N/A", "description": "Unlisted 2", "weight": "0.02"},
                {"symbol": "aapl", "description": "Apple Inc dup, different casing", "weight": "0.07"},
                {"symbol": " MSFT ", "description": "Microsoft, padded", "weight": "0.05"},
                {"symbol": "--", "description": "Placeholder", "weight": "0.01"}
              ]
            }
            """.trimIndent()
        whenever(alphaProxy.getEtfProfile(eq("VOO"), any())).thenReturn(body)

        val result = enricher.enrichClassification(etf("VOO"))

        assertThat(result).isEqualTo(EnrichmentResult.ENRICHED)
        verify(classificationService).clearExposures("etf-1")
        verify(classificationService).clearHoldings("etf-1")

        // Sector exposures are written even though the holdings list contained junk rows.
        verify(classificationService, times(2)).addExposure(any(), any(), any(), any(), any())

        // Only the two real symbols are persisted, de-duplicated case-insensitively and stored in
        // normalized form - the asset_holding unique constraint is case-sensitive, so persisting
        // raw casing would let "aapl" and "AAPL" both land for the same parent.
        val symbols = argumentCaptor<String>()
        verify(classificationService, times(2)).addHolding(any(), symbols.capture(), anyOrNull(), any(), any())
        assertThat(symbols.allValues).containsExactlyInAnyOrder("AAPL", "MSFT")
    }

    @Test
    fun `rate limit body returns RATE_LIMITED and does not clear existing data`() {
        val body =
            """{"Information": "We have detected your API key as XXXX and our standard API """ +
                """rate limit is 25 requests per day. Please visit https://www.alphavantage.co"}"""
        whenever(alphaProxy.getEtfProfile(eq("VOO"), any())).thenReturn(body)

        val result = enricher.enrichClassification(etf("VOO"))

        assertThat(result).isEqualTo(EnrichmentResult.RATE_LIMITED)
        verify(classificationService, never()).clearExposures(any())
        verify(classificationService, never()).clearHoldings(any())
        verify(classificationService, never()).addExposure(any(), any(), any(), any(), any())
        verify(classificationService, never()).addHolding(any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun `rate limit body on equity path also returns RATE_LIMITED`() {
        val body =
            """{"Note": "Thank you for using Alpha Vantage! Our standard API rate limit """ +
                """is 25 requests per day."}"""
        whenever(alphaProxy.getOverview(eq("AAPL"), any())).thenReturn(body)

        val result = enricher.enrichClassification(equity("AAPL"))

        assertThat(result).isEqualTo(EnrichmentResult.RATE_LIMITED)
        verify(classificationService, never()).classifyAsset(any(), any(), any(), any(), any())
    }

    @Test
    fun `well-formed ETF response with no sectors and no holdings returns NO_DATA`() {
        whenever(alphaProxy.getEtfProfile(eq("VOO"), any())).thenReturn("""{"net_assets": "1000"}""")

        val result = enricher.enrichClassification(etf("VOO"))

        assertThat(result).isEqualTo(EnrichmentResult.NO_DATA)
        verify(classificationService, never()).clearExposures(any())
        verify(classificationService, never()).clearHoldings(any())
    }

    @Test
    fun `well-formed equity response with no sector returns NO_DATA`() {
        whenever(alphaProxy.getOverview(eq("AAPL"), any())).thenReturn("""{"Symbol": "AAPL"}""")

        val result = enricher.enrichClassification(equity("AAPL"))

        assertThat(result).isEqualTo(EnrichmentResult.NO_DATA)
        verify(classificationService, never()).classifyAsset(any(), any(), any(), any(), any())
    }

    @Test
    fun `error response returns FAILED`() {
        whenever(alphaProxy.getOverview(eq("AAPL"), any())).thenReturn("""{"Error Message": "Invalid API call"}""")

        val result = enricher.enrichClassification(equity("AAPL"))

        assertThat(result).isEqualTo(EnrichmentResult.FAILED)
    }

    @Test
    fun `blank response returns FAILED`() {
        whenever(alphaProxy.getEtfProfile(eq("VOO"), any())).thenReturn("")

        val result = enricher.enrichClassification(etf("VOO"))

        assertThat(result).isEqualTo(EnrichmentResult.FAILED)
    }

    @Test
    fun `exception thrown by the proxy returns FAILED`() {
        whenever(alphaProxy.getOverview(eq("AAPL"), any())).thenThrow(RuntimeException("boom"))

        val result = enricher.enrichClassification(equity("AAPL"))

        assertThat(result).isEqualTo(EnrichmentResult.FAILED)
    }

    @Test
    fun `enrichEquity classifies sector and industry`() {
        val body = """{"Symbol": "AAPL", "Sector": "Technology", "Industry": "Consumer Electronics"}"""
        whenever(alphaProxy.getOverview(eq("AAPL"), any())).thenReturn(body)

        val result = enricher.enrichClassification(equity("AAPL"))

        assertThat(result).isEqualTo(EnrichmentResult.ENRICHED)
        verify(classificationService, times(2)).classifyAsset(any(), any(), any(), any(), any())
    }

    /**
     * AlphaVantage's throttle wording is not stable - this variant names a call frequency and
     * never says "rate limit". Matching on the phrasing rather than the key would classify it as
     * NO_DATA, stamping the asset as checked even though it was never really looked at.
     */
    @Test
    fun `throttle body without the words rate limit still returns RATE_LIMITED`() {
        val body =
            """{"Note": "Thank you for using Alpha Vantage! Our standard API call frequency is """ +
                """5 calls per minute and 500 calls per day."}"""
        whenever(alphaProxy.getEtfProfile(eq("VOO"), any())).thenReturn(body)

        val result = enricher.enrichClassification(etf("VOO"))

        assertThat(result).isEqualTo(EnrichmentResult.RATE_LIMITED)
        verify(classificationService, never()).clearExposures(any())
        verify(classificationService, never()).clearHoldings(any())
    }

    /**
     * A non-US-listed ETF resolves to a suffixed symbol AlphaVantage does not cover (e.g.
     * `NATO.LON`), which answers `{}`. It must read as NO_DATA - never as the US-listed namesake's
     * profile, and never as a failure worth retrying immediately. kauri holds two distinct funds
     * both coded NATO: THEMES TRANSATLANTIC DEFENSE on the US side and FUTURE OF DEFENCE UCITS on
     * LON.
     */
    @Test
    fun `ETF on a market AlphaVantage does not cover returns NO_DATA and writes nothing`() {
        whenever(alphaProxy.getEtfProfile(eq("NATO.LON"), any())).thenReturn("{}")

        val result = enricher.enrichClassification(etf("NATO.LON"))

        assertThat(result).isEqualTo(EnrichmentResult.NO_DATA)
        verify(classificationService, never()).clearExposures(any())
        verify(classificationService, never()).addExposure(any(), any(), any(), any(), any())
        verify(classificationService, never()).addHolding(any(), any(), anyOrNull(), any(), any())
    }

    /**
     * NO_DATA stamps `classificationCheckedAt`, so the asset drops behind the staleness window
     * and is not looked at again for a week. At DEBUG that outcome left no trace in a normal
     * kauri log - VUAA sat with zero sector exposures for months with nothing to grep for. The
     * warning has to name the provider and the exact symbol that was asked for: the symbol is
     * composed from the market alias, so it is the one thing that identifies a mapping fault.
     */
    @Test
    fun `an empty ETF profile warns with provider and the symbol that was queried`() {
        whenever(alphaProxy.getEtfProfile(eq("VUAA.LON"), any())).thenReturn("{}")

        val warnings = captureWarnings { enricher.enrichClassification(etf("VUAA.LON")) }

        assertThat(warnings)
            .describedAs("empty ETF profile must be visible at WARN")
            .anySatisfy { message ->
                assertThat(message).contains("ALPHA").contains("VUAA.LON")
            }
    }

    @Test
    fun `an overview carrying no sector warns with provider and the symbol that was queried`() {
        whenever(alphaProxy.getOverview(eq("TSCO.LON"), any())).thenReturn("{}")

        val warnings = captureWarnings { enricher.enrichClassification(equity("TSCO.LON")) }

        assertThat(warnings)
            .describedAs("empty overview must be visible at WARN")
            .anySatisfy { message ->
                assertThat(message).contains("ALPHA").contains("TSCO.LON")
            }
    }

    private fun captureWarnings(block: () -> Unit): List<String> {
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val logger = LoggerFactory.getLogger(AlphaClassificationEnricher::class.java) as Logger
        logger.addAppender(appender)
        try {
            block()
        } finally {
            logger.detachAppender(appender)
        }
        return appender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }
    }
}