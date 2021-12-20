package com.beancounter.marketdata.markets

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.marketdata.Constants.Companion.NZX
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.providers.MdFactory
import com.beancounter.marketdata.providers.alpha.AlphaService
import com.beancounter.marketdata.providers.cash.CashProviderService
import com.beancounter.marketdata.providers.wtd.WtdService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.AssertionsForClassTypes
import org.junit.jupiter.api.Assertions
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
@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("test")
class MarketDataProviderTests @Autowired constructor(
    private val mdFactory: MdFactory,
    private val marketService: MarketService
) {
    @Test
    fun is_DefaultMarketProvidersSet() {
        AssertionsForClassTypes.assertThat(mdFactory.getMarketDataProvider(WtdService.ID)).isNotNull
        AssertionsForClassTypes.assertThat(mdFactory.getMarketDataProvider(AlphaService.ID)).isNotNull
        AssertionsForClassTypes.assertThat(mdFactory.getMarketDataProvider(CashProviderService.ID)).isNotNull
        val mdp = mdFactory.getMarketDataProvider(
            Market("NonExistent", Currency("ABC"))
        )
        assertThat(mdp)
            .isNotNull
            .hasFieldOrPropertyWithValue("id", CashProviderService.ID)
    }

    @Test
    fun is_FoundByMarket() {
        val amp = getAsset(marketService.getMarket("ASX"), "AMP")
        val asxMarket = mdFactory.getMarketDataProvider(amp.market)
        assertThat(asxMarket!!.getId()).isEqualTo(AlphaService.ID)
        val gne = getAsset(marketService.getMarket(NZX.code), "GNE")
        val nzxMarket = mdFactory.getMarketDataProvider(gne.market)
        AssertionsForClassTypes.assertThat(nzxMarket!!.getId()).isEqualTo(WtdService.ID)
        AssertionsForClassTypes.assertThat(nzxMarket.isMarketSupported(gne.market)).isTrue
        AssertionsForClassTypes.assertThat(nzxMarket.isMarketSupported(amp.market)).isFalse
    }

    @Test
    fun is_InvalidMarketException() {
        Assertions.assertThrows(BusinessException::class.java) { marketService.getMarket("illegal") }
    }
}
