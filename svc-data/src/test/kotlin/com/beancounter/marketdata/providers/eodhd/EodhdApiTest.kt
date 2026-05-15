package com.beancounter.marketdata.providers.eodhd

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.model.Market
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.AAPL
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.providers.eodhd.EodhdPriceService.Companion.ID
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
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
@AutoConfigureWireMock(port = 0)
@AutoConfigureMockAuth
internal class EodhdApiTest {
    private val dateUtils = DateUtils()

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var eodhdPriceService: EodhdPriceService

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

    private fun stubEod(
        symbol: String,
        fixturePath: String
    ) {
        val body = ClassPathResource(fixturePath).file.readText()
        stubFor(
            get(urlPathEqualTo("/api/eod/$symbol"))
                .willReturn(
                    aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(body)
                        .withStatus(200)
                )
        )
    }
}