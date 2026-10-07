package com.beancounter.marketdata.classification

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetFundamentals
import com.beancounter.common.model.ClassificationLevel
import com.beancounter.common.model.ClassificationStandard
import com.beancounter.common.model.Market
import com.beancounter.common.model.Status
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.providers.sec.SecProxy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal
import java.time.LocalDate

/**
 * SEC EDGAR enrichment against a real H2-backed [ClassificationService] and
 * [AssetFundamentalsRepository]; only the HTTP edge ([SecProxy]) is mocked. Assertions are on
 * persisted state - the sector/industry rows under the SEC standard and the fundamentals
 * snapshot - not on mock interactions.
 */
@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("h2db")
@AutoConfigureMockAuth
internal class SecClassificationEnricherTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var secProxy: SecProxy

    @Autowired
    private lateinit var enricher: SecClassificationEnricher

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var classificationRepository: AssetClassificationRepository

    @Autowired
    private lateinit var fundamentalsRepository: AssetFundamentalsRepository

    private val nasdaq = Market("NASDAQ")

    private fun fixture(name: String) = ClassPathResource("mock/sec/$name").file.readText()

    private fun transientAsset(
        code: String,
        category: String = "Equity"
    ) = Asset(id = "$code-id", code = code, name = code, category = category, market = nasdaq, status = Status.Active)

    private fun persistedAsset(code: String): Asset =
        assetService
            .handle(AssetRequest(mapOf(code to AssetInput(nasdaq.code, code, name = code))))
            .data
            .getValue(code)

    @BeforeEach
    fun stubSec() {
        whenever(secProxy.getCompanyTickers()).thenReturn(fixture("company_tickers.json"))
        whenever(secProxy.getSubmissions("0000320193")).thenReturn(fixture("submissions-AAPL.json"))
        whenever(secProxy.getCompanyFacts("0000320193")).thenReturn(fixture("companyfacts-AAPL.json"))
    }

    @Test
    fun `should classify a US equity by SIC and persist its fundamentals snapshot`() {
        val asset = persistedAsset("AAPL")

        val result = enricher.enrichClassification(asset)

        assertThat(result).isEqualTo(EnrichmentResult.ENRICHED)

        val classifications = classificationRepository.findByAssetId(asset.id)
        assertThat(classifications).hasSize(2)
        assertThat(classifications.map { it.standard.provider }).containsOnly(ClassificationStandard.PROVIDER_SEC)
        val byLevel = classifications.associateBy { it.level }
        assertThat(byLevel.getValue(ClassificationLevel.SECTOR).item.name).isEqualTo("Information Technology")
        assertThat(byLevel.getValue(ClassificationLevel.INDUSTRY).item.name).isEqualTo("Electronic Computers")

        val fundamentals = fundamentalsRepository.findById(asset.id).orElseThrow()
        assertThat(fundamentals.source).isEqualTo(AssetFundamentals.SOURCE_SEC)
        assertThat(fundamentals.fiscalYear).isEqualTo(2025)
        assertThat(fundamentals.fiscalYearEnd).isEqualTo(LocalDate.of(2025, 9, 27))
        assertThat(fundamentals.epsDiluted).isEqualByComparingTo(BigDecimal("7.46"))
        assertThat(fundamentals.revenue).isEqualByComparingTo(BigDecimal("416161000000"))
        assertThat(fundamentals.netIncome).isEqualByComparingTo(BigDecimal("112010000000"))
        assertThat(fundamentals.dividendsPerShare).isEqualByComparingTo(BigDecimal("1.02"))
        assertThat(fundamentals.sharesOutstanding).isEqualTo(14773123000L)
    }

    @Test
    fun `should overwrite rather than duplicate the fundamentals row on a second pass`() {
        val asset = persistedAsset("AAPL")

        val first = enricher.enrichClassification(asset)
        // A newer 10-K lands between runs: the snapshot must move to it, on the same row.
        whenever(secProxy.getCompanyFacts("0000320193")).thenReturn(
            fixture("companyfacts-AAPL.json")
                .replace("\"end\":\"2025-09-27\",\"val\":7.46", "\"end\":\"2026-09-26\",\"val\":8.10")
                .replace(
                    "\"frame\":\"CY2025\"},{\"start\":\"2025-06-29\"",
                    "\"frame\":\"CY2026\"},{\"start\":\"2025-06-29\""
                )
        )
        val second = enricher.enrichClassification(asset)

        assertThat(listOf(first, second)).containsOnly(EnrichmentResult.ENRICHED)
        assertThat(fundamentalsRepository.findAll().filter { it.assetId == asset.id }).hasSize(1)
        assertThat(fundamentalsRepository.findById(asset.id).orElseThrow().epsDiluted)
            .isEqualByComparingTo(BigDecimal("8.10"))
        assertThat(classificationRepository.findByAssetId(asset.id)).hasSize(2)
    }

    @Test
    fun `should only offer to enrich equities on US exchanges`() {
        assertThat(enricher.canEnrich(transientAsset("AAPL"))).isTrue()
        assertThat(enricher.canEnrich(transientAsset("VTI", category = "ETF"))).isFalse()
        assertThat(
            enricher.canEnrich(
                Asset(id = "x", code = "BHP", name = "BHP", market = Market("ASX"), status = Status.Active)
            )
        ).isFalse()
    }

    @Test
    fun `should report NO_DATA for an ETF since the SEC carries no sector weights`() {
        // A ticker the SEC index DOES list, so only the fund guard can produce NO_DATA here.
        val etf = transientAsset("AAPL", category = "ETF")

        assertThat(enricher.enrichClassification(etf)).isEqualTo(EnrichmentResult.NO_DATA)
        assertThat(classificationRepository.findByAssetId(etf.id)).isEmpty()
        assertThat(fundamentalsRepository.findById(etf.id)).isEmpty
        verify(secProxy, never()).getSubmissions(any())
    }

    @Test
    fun `should report NO_DATA for a ticker the SEC does not list`() {
        val unknown = transientAsset("ZZZZ")

        assertThat(enricher.enrichClassification(unknown)).isEqualTo(EnrichmentResult.NO_DATA)
        assertThat(classificationRepository.findByAssetId(unknown.id)).isEmpty()
    }

    @Test
    fun `should report RATE_LIMITED on HTTP 429 so the asset is retried first next run`() {
        whenever(secProxy.getSubmissions(any())).thenThrow(
            HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                HttpHeaders(),
                ByteArray(0),
                null
            )
        )

        assertThat(enricher.enrichClassification(transientAsset("AAPL"))).isEqualTo(EnrichmentResult.RATE_LIMITED)
    }

    @Test
    fun `should report FAILED on an unexpected HTTP error`() {
        whenever(secProxy.getSubmissions(any())).thenThrow(
            HttpClientErrorException.create(HttpStatus.BAD_GATEWAY, "Bad Gateway", HttpHeaders(), ByteArray(0), null)
        )

        assertThat(enricher.enrichClassification(transientAsset("AAPL"))).isEqualTo(EnrichmentResult.FAILED)
    }
}