package com.beancounter.marketdata

import com.beancounter.common.model.Asset
import com.beancounter.marketdata.assets.AssetEnricher
import com.beancounter.marketdata.assets.figi.FigiEnricher
import com.beancounter.marketdata.providers.alpha.AlphaConfig
import com.beancounter.marketdata.providers.alpha.AlphaEnricher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Verifies that default enricher behaviour is correct.
 */
class EnrichmentTest {
    private val code = "code"
    private val name = "test"

    @Test
    fun is_FigiEnrichment() {
        val enricher: AssetEnricher = FigiEnricher()
        val asset = Asset(code)
        assertThat(enricher.canEnrich(asset)).isTrue
        asset.name = name
        assertThat(enricher.canEnrich(asset)).isFalse
    }

    @Test
    fun is_AlphaEnrichment() {
        val enricher: AssetEnricher = AlphaEnricher(AlphaConfig())
        val asset = Asset(code)
        assertThat(enricher.canEnrich(asset)).isTrue
        asset.name = name
        assertThat(enricher.canEnrich(asset)).isFalse
    }
}
