package com.beancounter.marketdata.classification.refresh

import com.beancounter.common.model.Asset
import com.beancounter.common.model.ClassificationLevel
import com.beancounter.common.model.ClassificationStandard
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.classification.ClassificationService
import com.beancounter.marketdata.classification.ClassificationTaxonomy
import com.beancounter.marketdata.classification.FundLookThrough
import com.beancounter.marketdata.classification.enricher.ClassificationEnricher
import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The classification backfill's candidate query: an asset with no classification data yet. A
 * fund counts as classified once it has exposures; anything else once it has a classification.
 */
@SpringMvcDbTest
@Transactional
class UnclassifiedAssetQueryTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var assetRepository: AssetRepository

    @Autowired
    private lateinit var classificationService: ClassificationService

    @Autowired
    private lateinit var fundLookThrough: FundLookThrough

    @Autowired
    private lateinit var taxonomy: ClassificationTaxonomy

    private val allCategories =
        ClassificationEnricher.EQUITY_CATEGORIES + ClassificationEnricher.ETF_CATEGORIES

    @Test
    fun `should return only assets with no classification data in the enrichable categories`() {
        val bareEquity = save("uq-bare-eq", "Equity")
        val classifiedEquity = save("uq-classified-eq", "Equity")
        val bareEtf = save("uq-bare-etf", "ETF")
        val exposedEtf = save("uq-exposed-etf", "ETF")
        val sectorOnlyEtf = save("uq-sector-etf", "ETF")
        val cash = save("uq-cash", "Cash")
        classificationService.classify(classifiedEquity, ClassificationStandard.ALPHA, "TECHNOLOGY", null, "TEST")
        classificationService.classify(sectorOnlyEtf, ClassificationStandard.ALPHA, "TECHNOLOGY", null, "TEST")
        val standard = taxonomy.providerStandard(ClassificationStandard.ALPHA)
        fundLookThrough.addExposure(
            exposedEtf,
            standard,
            taxonomy.getOrCreateItem(standard, ClassificationLevel.SECTOR, rawCode = "TECHNOLOGY"),
            BigDecimal.ONE,
            LocalDate.of(2026, 1, 2)
        )

        val ids =
            assetRepository
                .findUnclassified(allCategories, ClassificationEnricher.ETF_CATEGORIES)
                .map { it.id }

        assertThat(ids)
            .contains(bareEquity.id, bareEtf.id, sectorOnlyEtf.id)
            .doesNotContain(classifiedEquity.id, exposedEtf.id, cash.id)
    }

    @Test
    fun `should return only the requested category`() {
        val bareEquity = save("uq-cat-eq", "Equity")
        val bareEtf = save("uq-cat-etf", "ETF")

        val ids =
            assetRepository
                .findUnclassified(setOf("ETF"), ClassificationEnricher.ETF_CATEGORIES)
                .map { it.id }

        assertThat(ids).contains(bareEtf.id).doesNotContain(bareEquity.id)
    }

    private fun save(
        id: String,
        category: String
    ): Asset =
        assetRepository.save(
            Asset(
                id = id,
                code = id.uppercase(),
                name = id,
                market = NASDAQ,
                marketCode = NASDAQ.code,
                category = category
            )
        )
}