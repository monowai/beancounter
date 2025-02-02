package com.beancounter.marketdata.cash

import com.beancounter.marketdata.Constants.Companion.EUR
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.currency.CurrencyService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Test the Cash Service.
 */
@SpringBootTest
class CashServiceTest {
    @MockitoBean
    private lateinit var currencyService: CurrencyService

    @MockitoBean
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var cashService: CashService

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `createCashBalanceAssets should create assets for each currency`() {
        val currencies = listOf(USD, EUR, NZD)

        Mockito.`when`(currencyService.currencies()).thenReturn(currencies)
        cashService.createCashBalanceAssets() // is invoked by a config class
        verify(assetService, times(2)).handle(any())
        assert(cashService.assets.size == currencies.size)
        currencies.forEach { currency ->
            assert(cashService.assets.containsKey(currency.code))
        }
    }

    @Test
    fun `find should return assets by market code`() {
        cashService.find()
        verify(assetService, times(1)).findByMarketCode(CASH)
    }
}