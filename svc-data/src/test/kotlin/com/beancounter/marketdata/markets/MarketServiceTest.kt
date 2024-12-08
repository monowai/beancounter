package com.beancounter.marketdata.markets

import com.beancounter.common.exception.BusinessException
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.AUD
import com.beancounter.marketdata.Constants.Companion.CASH_MARKET
import com.beancounter.marketdata.Constants.Companion.GBP
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.SGD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.providers.cash.CashProviderService
import com.beancounter.marketdata.providers.custom.OffMarketDataProvider
import com.beancounter.marketdata.providers.marketstack.MarketStackService.Companion.ID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Market Configuration data tests.
 */
@SpringMvcDbTest
class MarketServiceTest {
    @Autowired
    private lateinit var marketService: MarketService

    @MockBean
    private lateinit var currencyService: CurrencyService

    @BeforeEach
    fun mockCurrencyService() {
        `when`(currencyService.baseCurrency).thenReturn(USD)
        `when`(currencyService.getCode(USD.code)).thenReturn(USD)
        `when`(currencyService.getCode(NZD.code)).thenReturn(NZD)
        `when`(currencyService.getCode(AUD.code)).thenReturn(AUD)
        `when`(currencyService.getCode(SGD.code)).thenReturn(SGD)
        `when`(currencyService.getCode(GBP.code)).thenReturn(GBP)
    }

    @Test
    fun is_FoundForAlias() {
        val nyse = marketService.getMarket(Constants.NYSE.code)
        val nzx = marketService.getMarket(Constants.NZX.code)
        val asx = marketService.getMarket(Constants.ASX.code)
        val nasdaq = marketService.getMarket(Constants.NASDAQ.code)
        assertThat(marketService.getMarket("nys")).isEqualTo(nyse)
        assertThat(marketService.getMarket("NZ")).isEqualTo(nzx)
        assertThat(marketService.getMarket("XASX")).isEqualTo(asx)
        assertThat(marketService.getMarket("NAS")).isEqualTo(nasdaq)
        assertThat(
            marketService.getMarket(OffMarketDataProvider.ID),
        ).isNotNull.hasFieldOrPropertyWithValue(
            "currencyId",
            USD.code,
        )
    }

    @Test
    fun does_MockMarketConfigurationExist() {
        val market = marketService.getMarket(CashProviderService.ID)
        assertThat(market)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                "timezone",
                TimeZone.getTimeZone(ZoneOffset.UTC),
            ).hasFieldOrProperty("currency")
            .hasFieldOrPropertyWithValue(
                "currency.code",
                USD.code,
            )
    }

    @Test
    fun is_IgnoreAliasLookup() {
        // Alias exists, but no PK with this code
        assertThrows(BusinessException::class.java) {
            marketService.getMarket(
                "NZ",
                false,
            )
        }
    }

    @Test
    fun is_AliasForMarketStackAndNzxResolving() {
        val market = marketService.getMarket(Constants.NZX.code)
        assertThat(market)
            .isNotNull
            .hasFieldOrProperty("aliases")
            .hasFieldOrPropertyWithValue(
                "currency.code",
                NZD.code,
            )
        assertThat(market.getAlias(ID)).isEqualTo("NZ").isNotNull
    }

    @Test
    fun does_MarketDataAliasNasdaqResolveToNull() {
        val market = marketService.getMarket(Constants.NASDAQ.code)
        assertThat(market).isNotNull.hasFieldOrProperty("aliases")
        assertThat(market.getAlias(ID)).isBlank
    }

    @Test
    fun is_IllegalArgumentsHandled() {
        assertThrows(BusinessException::class.java) {
            marketService.getMarket(
                null,
                true,
            )
        }
    }

    @Test
    fun is_CashMarketConfigured() {
        // Pseudo market for cash Assets.
        val market = marketService.getMarket(CASH_MARKET.code)
        assertThat(market).isNotNull
    }
}
