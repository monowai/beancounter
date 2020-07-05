package com.beancounter.marketdata

import com.beancounter.common.model.Asset
import com.beancounter.marketdata.assets.AssetEnricher
import com.beancounter.marketdata.assets.MockEnricher
import com.beancounter.marketdata.assets.figi.FigiEnricher
import com.beancounter.marketdata.providers.alpha.AlphaConfig
import com.beancounter.marketdata.providers.alpha.AlphaEnricher
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class EnrichmentTest {
    @Test
    fun is_FigiEnrichment() {
        val enricher: AssetEnricher = FigiEnricher()
        val asset = Asset("code")
        Assertions.assertThat(enricher.canEnrich(asset)).isTrue()
        asset.name = "test"
        Assertions.assertThat(enricher.canEnrich(asset)).isFalse()
    }

    @Test
    fun is_MockEnrichment() {
        val enricher: AssetEnricher = MockEnricher()
        val asset = Asset("code")
        Assertions.assertThat(enricher.canEnrich(asset)).isTrue()
        asset.name = "test"
        Assertions.assertThat(enricher.canEnrich(asset)).isFalse()
    }

    @Test
    fun is_AlphaEnrichment() {
        val enricher: AssetEnricher = AlphaEnricher(AlphaConfig())
        val asset = Asset("code")
        Assertions.assertThat(enricher.canEnrich(asset)).isTrue()
        asset.name = "test"
        Assertions.assertThat(enricher.canEnrich(asset)).isFalse()
    }
}