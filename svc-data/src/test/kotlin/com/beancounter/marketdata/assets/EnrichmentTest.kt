package com.beancounter.marketdata.assets

import com.beancounter.common.model.Asset
import com.beancounter.marketdata.Constants.Companion.NYSE
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
        val enricher: AssetEnricher = FigiEnricher(DefaultEnricher())
        val asset = Asset(id = "123", code = code, market = NYSE, name = null)
        assertThat(enricher.canEnrich(asset)).isTrue
        asset.name = name
        assertThat(enricher.canEnrich(asset)).isFalse
    }

    @Test
    fun is_AlphaEnrichment() {
        val enricher: AssetEnricher = AlphaEnricher(AlphaConfig(), DefaultEnricher())
        val asset = Asset(id = "123", code = code, market = NYSE, name = null)
        assertThat(enricher.canEnrich(asset)).isTrue
        asset.name = name
        assertThat(enricher.canEnrich(asset)).isFalse
    }
}
