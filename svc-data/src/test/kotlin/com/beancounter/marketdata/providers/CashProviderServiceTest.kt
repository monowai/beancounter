package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceRequest.Companion.of
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.marketdata.Constants.Companion.CASH
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.providers.cash.CashProviderService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Asserts CashProvider assumptions.
 *
 * @author mikeh
 * @since 2021-12-01
 */
internal class CashProviderServiceTest {
    @Test
    fun is_CashProviderReturningValues() {
        val provider: MarketDataPriceProvider = CashProviderService()
        val result = provider.getMarketData(of(getAsset(CASH, NZD.code)))
        assertThat(result)
            .isNotNull
            .isNotEmpty
        val marketData = result.iterator().next()
        assertThat(marketData)
            .hasFieldOrPropertyWithValue("close", BigDecimal.ONE)
    }
}
