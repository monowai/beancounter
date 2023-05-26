package com.beancounter.marketdata.markets

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Market
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.marketdata.Constants.Companion.NZX
import com.beancounter.marketdata.providers.MdFactory
import com.beancounter.marketdata.providers.alpha.AlphaPriceService
import com.beancounter.marketdata.providers.cash.CashProviderService
import com.beancounter.marketdata.providers.wtd.WtdService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Market related tests.
 *
 * @author mikeh
 * @since 2019-03-19
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockAuth
class MarketDataPriceProviderTests @Autowired constructor(
    private val mdFactory: MdFactory,
    private val marketService: MarketService,
) {
    @Test
    fun is_DefaultMarketProvidersSet() {
        assertThat(mdFactory.getMarketDataProvider(WtdService.ID)).isNotNull
        assertThat(mdFactory.getMarketDataProvider(AlphaPriceService.ID)).isNotNull
        assertThat(mdFactory.getMarketDataProvider(CashProviderService.ID)).isNotNull
        val mdp = mdFactory.getMarketDataProvider(
            Market("NonExistent", "ABC"),
        )
        assertThat(mdp)
            .isNotNull
            .hasFieldOrPropertyWithValue("id", CashProviderService.ID)
    }

    @Test
    fun is_FoundByMarket() {
        val amp = getAsset(marketService.getMarket("ASX"), "AMP")
        val asxMarket = mdFactory.getMarketDataProvider(amp.market)
        assertThat(asxMarket.getId()).isEqualTo(AlphaPriceService.ID)
        val gne = getAsset(marketService.getMarket(NZX.code), "GNE")
        val nzxMarket = mdFactory.getMarketDataProvider(gne.market)
        assertThat(nzxMarket.getId()).isEqualTo(WtdService.ID)
        assertThat(nzxMarket.isMarketSupported(gne.market)).isTrue
        assertThat(nzxMarket.isMarketSupported(amp.market)).isFalse
    }

    @Test
    fun is_InvalidMarketException() {
        assertThrows(BusinessException::class.java) { marketService.getMarket("illegal") }
    }
}
