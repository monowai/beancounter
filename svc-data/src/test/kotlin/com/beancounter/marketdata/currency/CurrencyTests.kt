package com.beancounter.marketdata.currency

import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Market related tests.
 *
 * @author mikeh
 * @since 2019-03-19
 */
@SpringMvcDbTest
internal class CurrencyTests
    @Autowired
    constructor(
        private val currencyService: CurrencyService,
    ) {
        @Test
        fun is_CurrencyDataLoading() {
            assertThat(currencyService.getCode(USD.code))
                .isNotNull
            assertThat(currencyService.baseCurrency)
                .isNotNull
        }
    }
