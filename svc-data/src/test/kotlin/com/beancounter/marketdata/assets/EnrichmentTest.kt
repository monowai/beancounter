package com.beancounter.marketdata.assets

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.common.utils.PreviousClosePriceDate
import com.beancounter.marketdata.Constants.Companion.NYSE
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.assets.figi.FigiEnricher
import com.beancounter.marketdata.providers.alpha.AlphaConfig
import com.beancounter.marketdata.providers.alpha.AlphaEnricher
import com.beancounter.marketdata.providers.alpha.AlphaProxy
import com.beancounter.marketdata.providers.custom.OffMarketDataProvider
import com.beancounter.marketdata.registration.SystemUserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * Verifies that default enricher behaviour is correct.
 */
class EnrichmentTest {
    private val code = "code"
    private val name = "test"
    private val id = "123"
    private val keyGenId = "UniqueId"

    @Test
    fun is_FigiEnrichment() {
        val enricher: AssetEnricher = FigiEnricher(DefaultEnricher())
        val asset = Asset(code = code, id = id, name = null, market = NYSE)
        assertThat(enricher.canEnrich(asset)).isTrue
        asset.name = name
        assertThat(enricher.canEnrich(asset)).isFalse
    }

    @Test
    fun is_AlphaEnrichment() {
        val dateUtils = DateUtils()
        val alphaProxy = Mockito.mock(AlphaProxy::class.java)
        val enricher: AssetEnricher =
            AlphaEnricher(
                AlphaConfig(dateUtils = dateUtils, PreviousClosePriceDate(dateUtils)),
                DefaultEnricher(),
                alphaProxy,
            )
        val asset = Asset(code = code, id = id, name = null, market = NYSE)
        assertThat(enricher.canEnrich(asset)).isTrue
        asset.name = name
        assertThat(enricher.canEnrich(asset)).isFalse
    }

    @Test
    fun is_OffMarketEnrichment() {
        val offMarket = Market(OffMarketDataProvider.ID)
        val keyGenUtils = Mockito.mock(KeyGenUtils::class.java)
        val systemUserService = Mockito.mock(SystemUserService::class.java)
        val enricher: AssetEnricher = OffMarketEnricher(systemUserService)
        val sysUserId = "sysUserId"
        Mockito.`when`(
            systemUserService.getOrThrow,
        ).thenReturn(SystemUser(sysUserId))

        Mockito.`when`(
            keyGenUtils.id,
        ).thenReturn(keyGenId)
        val asset = Asset(code = code, id = id, name = null, market = offMarket)
        assertThat(enricher.canEnrich(asset)).isTrue
        val enriched = enricher.enrich(asset.id, offMarket, AssetInput.toRealEstate(USD, code, "Anything", "test-user"))
        assertThat(enriched)
            .hasFieldOrPropertyWithValue("systemUser.id", sysUserId)
            .hasFieldOrPropertyWithValue("code", "$sysUserId.${code.uppercase()}")
    }
}
