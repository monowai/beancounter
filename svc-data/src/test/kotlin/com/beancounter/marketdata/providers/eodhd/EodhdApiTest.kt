package com.beancounter.marketdata.providers.eodhd

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.model.Market
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.marketdata.Constants.Companion.AAPL
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.providers.eodhd.EodhdPriceService.Companion.ID
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal

/**
 * EODHD provider tests using WireMock.
 */
@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("h2db", "eodhd")
@AutoConfigureMockAuth
internal class EodhdApiTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var eodhdPriceService: EodhdPriceService

    // Reset between tests so per-test stub/verify counters don't bleed into
    // siblings — the bulk-routing tests register `/api/eod-bulk-last-day/US`
    // stubs that would otherwise carry over and turn the per-symbol 404 test
    // into a bulk-path test.
    @AfterEach
    fun resetWireMock() = WireMock.reset()

    @Test
    fun `returns priced market data for a US symbol`() {
        stubEod("AAPL.US", "mock/eodhd/AAPL-US.json")

        val result =
            eodhdPriceService.getMarketData(
                PriceRequest(
                    "2024-11-29",
                    listOf(PriceAsset(AAPL))
                )
            )

        assertThat(result).hasSize(1)
        val md = result.first()
        assertThat(md.asset).isEqualTo(AAPL)
        assertThat(md.source).isEqualTo(ID)
        assertThat(md.close).isEqualByComparingTo(BigDecimal("237.33"))
        assertThat(md.open).isEqualByComparingTo(BigDecimal("234.81"))
    }

    @Test
    fun `routes LON symbols via the LSE exchange suffix`() {
        // BARC on the London Stock Exchange — confirms the `LON → LSE` alias
        // produces `BARC.LSE`, the EODHD ticker for Barclays.
        val barc = getTestAsset(Market("LON"), "BARC")
        stubEod("BARC.LSE", "mock/eodhd/BARC-LON.json")

        val result =
            eodhdPriceService.getMarketData(
                PriceRequest(
                    "2024-11-29",
                    listOf(PriceAsset(barc))
                )
            )

        assertThat(result).hasSize(1)
        assertThat(result.first().close).isEqualByComparingTo(BigDecimal("274.85"))
        assertThat(result.first().source).isEqualTo(ID)
    }

    @Test
    fun `backFill streams historical EOD rows for the requested asset`() {
        // Exercises EodhdPriceService.backFill → EodhdProxy.getHistory → EodhdGateway.getHistory
        // end-to-end. The history fixture carries two trading days; both should round-trip.
        stubEod("AAPL.US", "mock/eodhd/AAPL-US-history.json")

        val result = eodhdPriceService.backFill(AAPL)

        assertThat(result.data).hasSize(2)
        val byDate = result.data.associateBy { it.priceDate.toString() }
        assertThat(byDate["2024-11-29"]?.close).isEqualByComparingTo(BigDecimal("237.33"))
        assertThat(byDate["2024-11-27"]?.close).isEqualByComparingTo(BigDecimal("234.93"))
        result.data.forEach { md -> assertThat(md.source).isEqualTo(ID) }
    }

    @Test
    fun `provider metadata exposes EODHD identity and the configured allowlist`() {
        // Cheap surface checks for MarketDataPriceProvider plumbing. The h2db/eodhd test
        // profile sets markets to "NASDAQ,AMEX,NYSE,LON" so US-aliased and LON markets are
        // supported while APAC markets fall through to other providers.
        assertThat(eodhdPriceService.getId()).isEqualTo(ID)
        assertThat(eodhdPriceService.isApiSupported()).isTrue
        assertThat(eodhdPriceService.isMarketSupported(Market("NASDAQ"))).isTrue
        assertThat(eodhdPriceService.isMarketSupported(Market("LON"))).isTrue
        assertThat(eodhdPriceService.isMarketSupported(Market("ASX"))).isFalse
    }

    @Test
    fun `getDate resolves a previous-close date for the requested market`() {
        // Smoke-tests the EodhdConfig.getMarketDate plumbing — non-null, ISO format, no throw.
        val date =
            eodhdPriceService.getDate(
                Market("NASDAQ"),
                PriceRequest("2024-11-29", emptyList())
            )

        assertThat(date).isNotNull
        assertThat(date.toString()).matches("\\d{4}-\\d{2}-\\d{2}")
    }

    @Test
    fun `multi-symbol batch routes through the bulk last-day endpoint`() {
        // Regression: pre-bulk implementation hit `/api/eod/{symbol}` once per asset,
        // taking ~1s × N round-trips on the cold-cache PortfolioValuationSchedule
        // fan-out. Bulk endpoint collapses N requests to 1; this asserts the routing
        // change holds AND both symbols' prices come back mapped to the right assets.
        val body = ClassPathResource("mock/eodhd/bulk-US-AAPL-MSFT.json").file.readText()
        wireMock.stubFor(
            get(urlPathEqualTo(BULK_LAST_DAY_US))
                .willReturn(
                    aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(body)
                        .withStatus(200)
                )
        )

        val result =
            eodhdPriceService.getMarketData(
                PriceRequest(
                    "2024-11-29",
                    listOf(PriceAsset(AAPL), PriceAsset(MSFT))
                )
            )

        // Bulk endpoint called once with both symbols and the requested date as query params.
        WireMock.verify(
            1,
            getRequestedFor(urlPathEqualTo(BULK_LAST_DAY_US))
                .withQueryParam("symbols", equalTo("AAPL.US,MSFT.US"))
                .withQueryParam("date", equalTo("2024-11-29"))
        )

        // Per-symbol fallback path NOT exercised.
        WireMock.verify(0, getRequestedFor(urlPathEqualTo("/api/eod/AAPL.US")))
        WireMock.verify(0, getRequestedFor(urlPathEqualTo("/api/eod/MSFT.US")))

        assertThat(result).hasSize(2)
        val byCode = result.associateBy { it.asset.code }
        assertThat(byCode["AAPL"]?.close).isEqualByComparingTo(BigDecimal("237.33"))
        assertThat(byCode["MSFT"]?.close).isEqualByComparingTo(BigDecimal("423.46"))
        result.forEach { md -> assertThat(md.source).isEqualTo(ID) }
    }

    @Test
    fun `missing symbol in bulk response yields a zero-close row for parity with per-symbol path`() {
        // Bulk response only carries AAPL — MSFT was requested but EODHD omitted it
        // (delisted, bad symbol, weekend). The adapter must still emit a row for MSFT
        // so downstream callers see every requested asset, matching per-symbol behaviour.
        val body = """[
            {
              "code": "AAPL",
              "exchange_short_name": "US",
              "date": "2024-11-29",
              "open": 234.81,
              "high": 237.81,
              "low": 233.97,
              "close": 237.33,
              "adjusted_close": 237.33,
              "volume": 28481377
            }
        ]"""
        wireMock.stubFor(
            get(urlPathEqualTo(BULK_LAST_DAY_US))
                .willReturn(
                    aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(body)
                        .withStatus(200)
                )
        )

        val result =
            eodhdPriceService.getMarketData(
                PriceRequest(
                    "2024-11-29",
                    listOf(PriceAsset(AAPL), PriceAsset(MSFT))
                )
            )

        assertThat(result).hasSize(2)
        val byCode = result.associateBy { it.asset.code }
        assertThat(byCode["AAPL"]?.close).isEqualByComparingTo(BigDecimal("237.33"))
        assertThat(byCode["MSFT"]?.close).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `404 on one symbol does not abort the whole batch`() {
        // Regression: a single Ticker-Not-Found used to propagate
        // HttpClientErrorException out of EodhdPriceService.getMarketData
        // and abort every other symbol in the same valuation cycle.
        wireMock.stubFor(
            get(urlPathEqualTo("/api/eod/MSFT.US"))
                .willReturn(aResponse().withStatus(404).withBody("Ticker Not Found."))
        )
        stubEod("AAPL.US", "mock/eodhd/AAPL-US.json")

        val result =
            eodhdPriceService.getMarketData(
                PriceRequest(
                    "2024-11-29",
                    listOf(PriceAsset(MSFT), PriceAsset(AAPL))
                )
            )

        assertThat(result).hasSize(1)
        assertThat(result.first().asset).isEqualTo(AAPL)
        assertThat(result.first().close).isEqualByComparingTo(BigDecimal("237.33"))
        assertThat(result.first().source).isEqualTo(ID)
    }

    @Test
    fun `maps adjusted_close to MarketData close so historical prices are split-adjusted`() {
        // EODHD's `/api/eod` ships both `close` (raw) and `adjusted_close` (split- and dividend-
        // adjusted). Beancounter stores MarketData.close as the *adjusted* price so historical
        // series remain comparable across splits. Regression: previously the adapter copied
        // `close` (raw), so pre-split prices showed as N× higher than current quotes, breaking
        // TWR / wealth charts after any split event.
        stubEod("AAPL.US", "mock/eodhd/AAPL-US-split.json")

        val result =
            eodhdPriceService.getMarketData(
                PriceRequest(
                    "2020-08-31",
                    listOf(PriceAsset(AAPL))
                )
            )

        assertThat(result).hasSize(1)
        // Fixture mirrors AAPL's 4-for-1 split on 2020-08-31: close=499.23, adjusted=124.81.
        assertThat(result.first().close).isEqualByComparingTo(BigDecimal("124.81"))
    }

    @Test
    fun `returns a zero-close row when EODHD has no data for the symbol`() {
        stubEod("MSFT.US", "mock/eodhd/no-data.json")

        val result =
            eodhdPriceService.getMarketData(
                PriceRequest(
                    "2024-11-29",
                    listOf(PriceAsset(MSFT))
                )
            )

        assertThat(result).hasSize(1)
        assertThat(result.first().close).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(result.first().source).isEqualTo(ID)
    }

    @Test
    fun `searchAssets surfaces EODHD search hits end-to-end`() {
        // Covers EodhdPriceService.searchAssets → EodhdProxy.searchAssets → EodhdGateway
        // through the real RestClient + WireMock, so all three layers see actual HTTP traffic
        // instead of unit-level mocks.
        val body = ClassPathResource("mock/eodhd/search-HYSA.json").file.readText()
        wireMock.stubFor(
            get(urlPathEqualTo("/api/search/HYSA"))
                .willReturn(
                    aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(body)
                        .withStatus(200)
                )
        )

        val results = eodhdPriceService.searchAssets("HYSA", "US")

        assertThat(results).hasSize(1)
        val hit = results.first()
        assertThat(hit.symbol).isEqualTo("HYSA")
        assertThat(hit.market).isEqualTo("US")
        assertThat(hit.currency).isEqualTo("USD")
        assertThat(hit.type).isEqualTo("ETF")
    }

    @Test
    fun `non-array bulk error body falls back to per-symbol instead of aborting the batch`() {
        // Regression POSITION-2F: a symbol on the wrong exchange (e.g. a LON-listed
        // ETF tagged US) makes EODHD return an error OBJECT, not the expected array.
        // RestClient then throws "Error while extracting response for type
        // [EodhdBulkPrice[]]" — NOT an HttpClientErrorException, so the old catch
        // missed it and the entire valuation 500'd, zeroing every portfolio in the
        // cycle. Must fall back to per-symbol so good symbols still resolve.
        wireMock.stubFor(
            get(urlPathEqualTo(BULK_LAST_DAY_US))
                .willReturn(
                    aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""{"code":"NOT_FOUND","message":"Symbols not found"}""")
                        .withStatus(200)
                )
        )
        stubEod("AAPL.US", "mock/eodhd/AAPL-US.json")
        stubEod("MSFT.US", "mock/eodhd/no-data.json")

        val result =
            eodhdPriceService.getMarketData(
                PriceRequest(
                    "2024-11-29",
                    listOf(PriceAsset(AAPL), PriceAsset(MSFT))
                )
            )

        // Bulk parse failed → per-symbol fallback exercised, batch not aborted.
        WireMock.verify(getRequestedFor(urlPathEqualTo("/api/eod/AAPL.US")))
        assertThat(result).hasSize(2)
        val byCode = result.associateBy { it.asset.code }
        assertThat(byCode["AAPL"]?.close).isEqualByComparingTo(BigDecimal("237.33"))
        assertThat(byCode["MSFT"]?.close).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `bulk I-O failure with failing per-symbol fallback logs one summary not per-symbol warnings`() {
        // Bulk endpoint and both per-symbol endpoints drop the connection (transient I/O —
        // e.g. a DNS/connection blip during the refresh burst). Bulk fails, falls back to
        // per-symbol, every per-symbol call also fails. This must emit ONE batch-summary
        // WARN, not one WARN per symbol — the burst previously logged ~50 identical lines.
        wireMock.stubFor(
            get(urlPathEqualTo(BULK_LAST_DAY_US))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
        )
        wireMock.stubFor(
            get(urlPathEqualTo("/api/eod/AAPL.US"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
        )
        wireMock.stubFor(
            get(urlPathEqualTo("/api/eod/MSFT.US"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
        )

        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val logger = LoggerFactory.getLogger(EodhdPriceService::class.java) as Logger
        logger.addAppender(appender)
        try {
            val result =
                eodhdPriceService.getMarketData(
                    PriceRequest(
                        "2024-11-29",
                        listOf(PriceAsset(AAPL), PriceAsset(MSFT))
                    )
                )
            assertThat(result).isEmpty()
        } finally {
            logger.detachAppender(appender)
        }

        val warnings = appender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }
        assertThat(warnings.filter { it.contains("per-symbol fallback") })
            .describedAs("one batch summary covering all failed symbols")
            .hasSize(1)
        assertThat(warnings.filter { it.contains("EODHD error for ") })
            .describedAs("no per-symbol warnings")
            .isEmpty()
    }

    private fun stubEod(
        symbol: String,
        fixturePath: String
    ) {
        val body = ClassPathResource(fixturePath).file.readText()
        wireMock.stubFor(
            get(urlPathEqualTo("/api/eod/$symbol"))
                .willReturn(
                    aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(body)
                        .withStatus(200)
                )
        )
    }

    companion object {
        const val BULK_LAST_DAY_US = "/api/eod-bulk-last-day/US"

        @JvmField
        @RegisterExtension
        val wireMock: WireMockExtension =
            WireMockExtension
                .newInstance()
                .options(WireMockConfiguration.options().dynamicPort())
                .configureStaticDsl(true)
                .build()

        @JvmStatic
        @org.springframework.test.context.DynamicPropertySource
        fun wireMockProps(registry: org.springframework.test.context.DynamicPropertyRegistry) {
            registry.add("wiremock.server.port") { wireMock.port }
        }
    }
}