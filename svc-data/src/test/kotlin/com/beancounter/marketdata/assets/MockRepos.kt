package com.beancounter.marketdata.assets

import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.currency.CurrencyRepository
import org.mockito.Mockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Configuration
import java.util.Optional

/**
 * Convenience class to prevent duplicate creation of mocks.
 */
@Configuration
class MockRepos {
    fun currencies() {
        Mockito.`when`(currencyRepository.findById(Constants.USD.code)).thenReturn(Optional.of(Constants.USD))
        Mockito.`when`(currencyRepository.findById(Constants.AUD.code)).thenReturn(Optional.of(Constants.AUD))
        Mockito.`when`(currencyRepository.findById(Constants.NZD.code)).thenReturn(Optional.of(Constants.NZD))
        Mockito.`when`(currencyRepository.findById(Constants.SGD.code)).thenReturn(Optional.of(Constants.SGD))
        Mockito.`when`(currencyRepository.findById(Constants.GBP.code)).thenReturn(Optional.of(Constants.GBP))
    }

    @MockBean
    lateinit var currencyRepository: CurrencyRepository

}