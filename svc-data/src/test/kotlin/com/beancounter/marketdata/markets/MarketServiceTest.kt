package com.beancounter.marketdata.markets

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.AUD
import com.beancounter.marketdata.Constants.Companion.CASH
import com.beancounter.marketdata.Constants.Companion.GBP
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.SGD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.providers.cash.CashProviderService
import com.beancounter.marketdata.providers.wtd.WtdService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Market Configuration data tests.
 */
@SpringBootTest(classes = [MarketService::class, DateUtils::class])
@ActiveProfiles("test")
@Tag("slow")
@EntityScan("com.beancounter.common.model")
class MarketServiceTest @Autowired constructor(
    private val marketService: MarketService,
) {
    @MockBean
    lateinit var currencyService: CurrencyService

    @BeforeEach
    fun mockCurrency() {
        Mockito.`when`(currencyService.getCode(USD.code)).thenReturn(USD)
        Mockito.`when`(currencyService.getCode(NZD.code)).thenReturn(NZD)
        Mockito.`when`(currencyService.getCode(AUD.code)).thenReturn(AUD)
        Mockito.`when`(currencyService.getCode(SGD.code)).thenReturn(SGD)
        Mockito.`when`(currencyService.getCode(GBP.code)).thenReturn(GBP)
    }

    @Test
    fun is_FoundForAlias() {
        val nyse = marketService.getMarket(Constants.NYSE.code)
        val nzx = marketService.getMarket(Constants.NZX.code)
        val asx = marketService.getMarket(Constants.ASX.code)
        val nasdaq = marketService.getMarket(Constants.NASDAQ.code)
        assertThat(marketService.getMarket("nys"))
            .isEqualTo(nyse)
            .hasFieldOrPropertyWithValue("type", "Public")
        assertThat(marketService.getMarket("NZ")).isEqualTo(nzx)
        assertThat(marketService.getMarket("AX")).isEqualTo(asx)
        assertThat(marketService.getMarket("NAS")).isEqualTo(nasdaq)
    }

    @Test
    fun does_MockMarketConfigurationExist() {
        val market = marketService.getMarket(CashProviderService.ID)
        assertThat(market)
            .isNotNull
            .hasFieldOrPropertyWithValue("timezone", TimeZone.getTimeZone(ZoneOffset.UTC))
            .hasFieldOrProperty("currency")
            .hasFieldOrPropertyWithValue("type", "Internal")
        assertThat(market.currency)
            .hasFieldOrPropertyWithValue("code", USD.code)
    }

    @Test
    fun is_IgnoreAliasLookup() {
        // Alias exists, but no PK with this code
        assertThrows(BusinessException::class.java) {
            marketService.getMarket(
                "US",
                false
            )
        }
    }

    @Test
    fun is_AliasForWtdAndNzxResolving() {
        val market = marketService.getMarket(Constants.NZX.code)
        assertThat(market)
            .isNotNull
            .hasFieldOrProperty("aliases")
        assertThat(market.currency)
            .hasFieldOrPropertyWithValue("code", NZD.code)
        assertThat(market.aliases[WtdService.ID])
            .isEqualTo("NZ")
            .isNotNull
    }

    @Test
    fun does_MarketDataAliasNasdaqResolveToNull() {
        val market = marketService.getMarket(Constants.NASDAQ.code)
        assertThat(market)
            .isNotNull
            .hasFieldOrProperty("aliases")
        assertThat(market.aliases[WtdService.ID])
            .isBlank
    }

    @Test
    fun is_IllegalArgumentsHandled() {
        assertThrows(BusinessException::class.java) { marketService.getMarket(null, true) }
    }

    @Test
    fun is_CashMarketConfigured() {
        // Pseudo market for cash Assets.
        val market = marketService.getMarket(CASH.code)
        assertThat(market)
            .isNotNull
            .hasFieldOrPropertyWithValue("type", "Internal")
    }
}
