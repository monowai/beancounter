package com.beancounter.marketdata.providers.eodhd

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
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