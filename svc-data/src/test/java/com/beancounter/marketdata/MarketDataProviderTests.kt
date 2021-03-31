package com.beancounter.marketdata

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.marketdata.Constants.Companion.NZX
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.alpha.AlphaService
import com.beancounter.marketdata.providers.mock.MockProviderService
import com.beancounter.marketdata.providers.wtd.WtdService
import com.beancounter.marketdata.service.MdFactory
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
class MarketDataProviderTests @Autowired constructor(private val mdFactory: MdFactory, private val marketService: MarketService) {
    companion object {
        @JvmStatic
        val mockCode = "MOCK"
    }
    @Test
    fun is_DefaultMarketProvidersSet() {
        AssertionsForClassTypes.assertThat(mdFactory.getMarketDataProvider(WtdService.ID)).isNotNull
        AssertionsForClassTypes.assertThat(mdFactory.getMarketDataProvider(AlphaService.ID)).isNotNull
        AssertionsForClassTypes.assertThat(mdFactory.getMarketDataProvider(MockProviderService.ID)).isNotNull
        val mdp = mdFactory.getMarketDataProvider(
            Market("NonExistent", Currency("ABC"))
        )
        AssertionsForClassTypes.assertThat(mdp)
            .isNotNull
            .hasFieldOrPropertyWithValue("ID", MockProviderService.ID)
    }

    @Test
    fun is_FoundByMarket() {
        val amp = getAsset(marketService.getMarket("ASX"), "AMP")
        val asxMarket = mdFactory.getMarketDataProvider(amp.market)
        AssertionsForClassTypes.assertThat(asxMarket!!.getId()).isEqualTo(AlphaService.ID)
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
