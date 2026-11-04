package com.beancounter.marketdata.classification.enricher

import com.beancounter.common.model.Asset
import com.beancounter.common.model.ClassificationItem
import com.beancounter.common.model.ClassificationLevel
import com.beancounter.common.model.ClassificationStandard
import com.beancounter.common.model.Market
import com.beancounter.common.model.Status
import com.beancounter.marketdata.classification.ClassificationTaxonomy
import com.beancounter.marketdata.classification.FundLookThrough
import com.beancounter.marketdata.providers.alpha.AlphaEtfHolding
import com.beancounter.marketdata.providers.alpha.AlphaEtfSector
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

/**
 * [AlphaEtfLookThrough] turns an ETF_PROFILE's sectors and holdings into fund look-through rows.
 */
class AlphaEtfLookThroughTest {
    private val taxonomy = mock<ClassificationTaxonomy>()
    private val fundLookThrough = mock<FundLookThrough>()
    private val lookThrough = AlphaEtfLookThrough(taxonomy, fundLookThrough)

    private val standard =
        ClassificationStandard(
            id = "std-alpha",
            key = ClassificationStandard.ALPHA,
            name = "AlphaVantage Sector Classification",
            provider = ClassificationStandard.PROVIDER_ALPHA
        )
    private val fund =
        Asset(
            id = "etf-1",
            code = "FUND",
            name = "ETF",
            category = "ETF",
            market = Market(code = "NASDAQ", name = "NASDAQ"),
            status = Status.Active
        )

    @Test
    fun `holdings are capped at the top ten accepted rows`() {
        val holdings =
            listOf(AlphaEtfHolding(symbol = "n/a", weight = "0.09")) +
                (1..12).map { AlphaEtfHolding(symbol = "S$it", description = "Holding $it", weight = "0.01") }

        val added = lookThrough.replaceTopHoldings(fund, holdings)

        assertThat(added).isEqualTo(AlphaEtfLookThrough.MAX_HOLDINGS)
        val symbols = argumentCaptor<String>()
        verify(fundLookThrough, times(AlphaEtfLookThrough.MAX_HOLDINGS))
            .addHolding(any(), symbols.capture(), anyOrNull(), any(), any())
        assertThat(symbols.allValues).containsExactly("S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8", "S9", "S10")
    }

    @Test
    fun `holdings without a positive weight are not written`() {
        val holdings =
            listOf(
                AlphaEtfHolding(symbol = "ZERO", weight = "0"),
                AlphaEtfHolding(symbol = "NEG", weight = "-0.01"),
                AlphaEtfHolding(symbol = "NONE", weight = null),
                AlphaEtfHolding(symbol = "KEEP", weight = "0.02")
            )

        assertThat(lookThrough.replaceTopHoldings(fund, holdings)).isEqualTo(1)
        verify(fundLookThrough).clearHoldings("etf-1")
    }

    @Test
    fun `only positively weighted sectors become exposures`() {
        whenever(taxonomy.providerStandard(ClassificationStandard.ALPHA)).thenReturn(standard)
        whenever(taxonomy.getOrCreateItem(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenAnswer {
                ClassificationItem(
                    id = "item",
                    standard = standard,
                    level = ClassificationLevel.SECTOR,
                    code = it.getArgument(3),
                    name = it.getArgument(3)
                )
            }
        val sectors =
            listOf(
                AlphaEtfSector(sector = "Technology", weight = "0.40"),
                AlphaEtfSector(sector = "Energy", weight = "0"),
                AlphaEtfSector(sector = "Utilities", weight = "unknown")
            )

        val added = lookThrough.replaceSectorExposures(fund, sectors)

        assertThat(added).isEqualTo(1)
        val weights = argumentCaptor<BigDecimal>()
        verify(fundLookThrough).clearExposures("etf-1")
        verify(fundLookThrough).addExposure(any(), any(), any(), weights.capture(), any())
        assertThat(weights.firstValue).isEqualByComparingTo("0.40")
    }
}