package com.beancounter.marketdata

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceRequest.Companion.of
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.marketdata.Constants.Companion.MOCK
import com.beancounter.marketdata.providers.mock.MockProviderService
import com.beancounter.marketdata.service.MarketDataProvider
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Data provider tests.
 *
 * @author mikeh
 * @since 2019-03-01
 */
internal class MockProviderServiceTest {
    @Test
    fun is_MockProviderReturningValues() {
        val asset = getAsset(MOCK, "Anything")
        val provider: MarketDataProvider = MockProviderService()
        val result = provider.getMarketData(of(asset))
        Assertions.assertThat(result)
            .isNotNull
            .isNotEmpty
        val marketData = result.iterator().next()
        Assertions.assertThat(marketData)
            .hasFieldOrPropertyWithValue("close", BigDecimal("999.99"))
    }

    @Test
    fun is_MockDataProviderThrowing() {
        // Hard coded asset exception
        val asset = getAsset(MOCK, "123")
        val provider: MarketDataProvider = MockProviderService()
        val priceRequest = PriceRequest(assets = setOf(AssetInput(MOCK.code, "123", asset)))
        org.junit.jupiter.api.Assertions.assertThrows(BusinessException::class.java) {
            provider.getMarketData(priceRequest)
        }
    }
}
