package com.beancounter.marketdata.currency

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.marketdata.Constants.Companion.USD
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Market related tests.
 *
 * @author mikeh
 * @since 2019-03-19
 */
@SpringBootTest
@EntityScan("com.beancounter.common.model")
@ActiveProfiles("test")
@Tag("db")
@AutoConfigureMockMvc
@AutoConfigureMockAuth
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
