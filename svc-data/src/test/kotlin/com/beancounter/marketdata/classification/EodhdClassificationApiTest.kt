package com.beancounter.marketdata.classification

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetClassification
import com.beancounter.common.model.ClassificationLevel
import com.beancounter.common.model.Market
import com.beancounter.common.model.Status
import com.beancounter.marketdata.MarketDataBoot
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal

/**
 * End-to-end WireMock coverage for EODHD classification: real EodhdConfig symbol routing,
 * eodhdRestClient HTTP, and [com.beancounter.marketdata.providers.eodhd.model.EodhdFundamentals]
 * JSON parsing, driven by payloads captured live from EODHD's `demo` token (VTI.US, AAPL.US).
 *
 * ClassificationService is mocked so the test exercises the HTTP + parse + routing path without DB
 * plumbing; the Morningstar→GICS sector mapping is proved separately by [SectorNormalizerEodhdTest].
 */
@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("h2db", "eodhd")
@AutoConfigureMockAuth
internal class EodhdClassificationApiTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var classificationService: ClassificationService

    @Autowired
    private lateinit var enricher: EodhdClassificationEnricher

    @AfterEach
    fun resetWireMock() = WireMock.reset()

    private val nasdaq = Market("NASDAQ")

    private fun asset(
        code: String,
        category: String
    ) = Asset(id = "$code-id", code = code, name = code, category = category, market = nasdaq, status = Status.Active)

    private fun stubFundamentals(
        symbol: String,
        fixture: String
    ) {
        val body = ClassPathResource("mock/eodhd/$fixture").file.readText()
        wireMock.stubFor(
            get(urlPathEqualTo("/api/fundamentals/$symbol"))
                .willReturn(
                    aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(body)
                        .withStatus(200)
                )
        )
    }

    @Test
    fun `ETF fundamentals stream real Sector_Weights into weighted exposures`() {
        // NASDAQ→US alias routes VTI to the EODHD ticker VTI.US.
        stubFundamentals("VTI.US", "VTI-fundamentals.json")

        val enriched = enricher.enrichClassification(asset("VTI", "ETF"))

        assertThat(enriched).isTrue()
        verify(classificationService).clearExposures("VTI-id")

        // All 11 EODHD sector names reach getOrCreateItem verbatim (normalization is downstream).
        val rawSectors = argumentCaptor<String>()
        verify(classificationService, times(11)).getOrCreateItem(
            anyOrNull(),
            eq(ClassificationLevel.SECTOR),
            anyOrNull(),
            rawSectors.capture(),
            anyOrNull()
        )
        assertThat(rawSectors.allValues).contains("Technology", "Consumer Cyclicals", "Financial Services")

        // Weights are parsed from the `Equity_%` strings.
        val weights = argumentCaptor<BigDecimal>()
        verify(classificationService, times(11)).addExposure(anyOrNull(), anyOrNull(), anyOrNull(), weights.capture(), anyOrNull())
        assertThat(weights.allValues).contains(BigDecimal("33.52294"))
        assertThat(weights.allValues.sumOf { it }).isCloseTo(BigDecimal("100"), org.assertj.core.data.Offset.offset(BigDecimal("0.5")))
    }

    @Test
    fun `equity fundamentals classify sector and industry from General`() {
        stubFundamentals("AAPL.US", "AAPL-fundamentals.json")

        val enriched = enricher.enrichClassification(asset("AAPL", "EQUITY"))

        assertThat(enriched).isTrue()

        val rawSector = argumentCaptor<String>()
        verify(classificationService).getOrCreateItem(anyOrNull(), eq(ClassificationLevel.SECTOR), anyOrNull(), rawSector.capture(), anyOrNull())
        assertThat(rawSector.firstValue).isEqualTo("Technology")

        val industryName = argumentCaptor<String>()
        verify(classificationService).getOrCreateItem(anyOrNull(), eq(ClassificationLevel.INDUSTRY), industryName.capture(), anyOrNull(), anyOrNull())
        assertThat(industryName.firstValue).isEqualTo("Consumer Electronics")

        val levels = argumentCaptor<ClassificationLevel>()
        verify(classificationService, times(2)).classifyAsset(anyOrNull(), anyOrNull(), anyOrNull(), levels.capture(), eq(AssetClassification.SOURCE_EODHD))
        assertThat(levels.allValues).containsExactly(ClassificationLevel.SECTOR, ClassificationLevel.INDUSTRY)
    }

    companion object {
        @JvmField
        @RegisterExtension
        val wireMock: WireMockExtension =
            WireMockExtension
                .newInstance()
                .options(WireMockConfiguration.options().dynamicPort())
                .configureStaticDsl(true)
                .build()

        @JvmStatic
        @DynamicPropertySource
        fun wireMockProps(registry: DynamicPropertyRegistry) {
            registry.add("wiremock.server.port") { wireMock.port }
        }
    }
}
