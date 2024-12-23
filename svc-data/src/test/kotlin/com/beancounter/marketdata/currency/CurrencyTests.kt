package com.beancounter.marketdata.currency

import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import org.assertj.core.api.Assertions.assertThat
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
internal class CurrencyTests
    @Autowired
    constructor(
        private val currencyService: CurrencyService
    ) {
        @MockitoBean
        private lateinit var jwtDecoder: JwtDecoder

        @Test
        fun is_CurrencyDataLoading() {
            assertThat(currencyService.getCode(USD.code))
                .isNotNull
            assertThat(currencyService.baseCurrency)
                .isNotNull
        }
    }