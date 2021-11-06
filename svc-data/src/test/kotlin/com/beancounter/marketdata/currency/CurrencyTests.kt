package com.beancounter.marketdata.currency

import com.beancounter.marketdata.Constants.Companion.USD
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Market related tests.
 *
 * @author mikeh
 * @since 2019-03-19
 */
@SpringBootTest(classes = [CurrencyService::class, CurrencyRepository::class])
@EntityScan("com.beancounter.common.model")
@EnableAutoConfiguration
@ActiveProfiles("test")
/**
 * Integration tests for static data related functionality.
 */
internal class CurrencyTests @Autowired constructor(
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
