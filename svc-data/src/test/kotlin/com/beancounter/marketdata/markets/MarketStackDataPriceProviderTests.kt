package com.beancounter.marketdata.markets

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Market
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.providers.MdFactory
import com.beancounter.marketdata.providers.alpha.AlphaPriceService
import com.beancounter.marketdata.providers.cash.CashProviderService
import com.beancounter.marketdata.providers.marketstack.MarketStackService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Market related tests.
 *
 * @author mikeh
 * @since 2019-03-19
 */
@SpringMvcDbTest
class MarketStackDataPriceProviderTests
    @Autowired
    constructor(
        private val mdFactory: MdFactory,
        private val marketService: MarketService,
        @MockitoBean private val jwtDecoder: JwtDecoder
    ) {
        @Test
        fun `should set default market providers`() {
            assertThat(mdFactory.getMarketDataProvider(MarketStackService.ID)).isNotNull
            assertThat(mdFactory.getMarketDataProvider(AlphaPriceService.ID)).isNotNull
            assertThat(mdFactory.getMarketDataProvider(CashProviderService.ID)).isNotNull
            val mdp =
                mdFactory.getMarketDataProvider(
                    Market(
                        "NonExistent",
                        "ABC"
                    )
                )
            assertThat(mdp)
                .isNotNull
                .hasFieldOrPropertyWithValue(
                    "id",
                    CashProviderService.ID
                )
        }

        @Test
        fun `configured market provider supports expected Market`() {
            val amp =
                getTestAsset(
                    marketService.getMarket("ASX"),
                    "AMP"
                )
            val marketProvider = mdFactory.getMarketDataProvider(amp.market)
            assertThat(marketProvider.getId()).isEqualTo(AlphaPriceService.ID)
        }

        @Test
        fun `should throw exception for invalid market`() {
            assertThrows(BusinessException::class.java) { marketService.getMarket("illegal") }
        }
    }