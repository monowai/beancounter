package com.beancounter.marketdata.classification

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Status
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * `beancounter.market.providers.classification` accepts a CSV. One value selects that enricher
 * as before; several wrap in [ChainedClassificationEnricher], which hands each asset to the first
 * enricher that can handle it.
 */
class ClassificationEnricherConfigTest {
    private val alpha: AlphaClassificationEnricher = mock()
    private val eodhd: EodhdClassificationEnricher = mock()
    private val sec: SecClassificationEnricher = mock()
    private val config = ClassificationEnricherConfig()

    private val asset =
        Asset(id = "a", code = "A", name = "A", market = Market("NASDAQ"), status = Status.Active)

    @Test
    fun `should select Alpha alone for the default single value`() {
        assertThat(config.classificationEnricher(alpha, eodhd, sec, "alpha")).isSameAs(alpha)
    }

    @Test
    fun `should select EODHD alone for a single eodhd value`() {
        assertThat(config.classificationEnricher(alpha, eodhd, sec, " EODHD ")).isSameAs(eodhd)
    }

    @Test
    fun `should chain SEC then Alpha for a CSV value`() {
        val enricher = config.classificationEnricher(alpha, eodhd, sec, "sec,alpha")

        assertThat(enricher).isInstanceOf(ChainedClassificationEnricher::class.java)
    }

    @Test
    fun `should delegate to the first enricher in the chain that can enrich the asset`() {
        whenever(sec.canEnrich(asset)).thenReturn(true)
        whenever(sec.enrichClassification(asset)).thenReturn(EnrichmentResult.NO_DATA)
        whenever(alpha.canEnrich(asset)).thenReturn(true)
        val enricher = config.classificationEnricher(alpha, eodhd, sec, "sec,alpha")

        val result = enricher.enrichClassification(asset)

        // NO_DATA from the first capable enricher does not fall through - deterministic.
        assertThat(result).isEqualTo(EnrichmentResult.NO_DATA)
        verify(alpha, never()).enrichClassification(any())
    }

    @Test
    fun `should fall to the next enricher when the first cannot enrich the asset`() {
        whenever(sec.canEnrich(asset)).thenReturn(false)
        whenever(alpha.canEnrich(asset)).thenReturn(true)
        whenever(alpha.enrichClassification(asset)).thenReturn(EnrichmentResult.ENRICHED)
        val enricher = config.classificationEnricher(alpha, eodhd, sec, "sec,alpha")

        assertThat(enricher.canEnrich(asset)).isTrue()
        assertThat(enricher.enrichClassification(asset)).isEqualTo(EnrichmentResult.ENRICHED)
        verify(sec, never()).enrichClassification(any())
    }

    @Test
    fun `should report NO_DATA when no enricher in the chain can enrich the asset`() {
        val enricher = config.classificationEnricher(alpha, eodhd, sec, "sec,alpha")

        assertThat(enricher.canEnrich(asset)).isFalse()
        assertThat(enricher.enrichClassification(asset)).isEqualTo(EnrichmentResult.NO_DATA)
    }
}