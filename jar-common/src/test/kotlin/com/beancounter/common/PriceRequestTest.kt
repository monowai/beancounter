package com.beancounter.common

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Market
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Unit tests for Price Contracts.
 */
class PriceRequestTest {
    @Test
    fun is_PriceRequestForAsset() {
        val priceRequest = PriceRequest.of(AssetUtils.getAsset(Market("NASDAQ"), "EBAY"))
        assertThat(priceRequest.assets).hasSize(1)
        assertThat(priceRequest.date).isEqualTo(DateUtils.today)
    }

    @Test
    @Disabled
    fun is_OfAssetInput() {
        val priceRequest = PriceRequest.of(AssetInput(market = "ABC", code = "123"))
        assertThat(priceRequest.assets).hasSize(1)
        val dateUtils = DateUtils()
        assertThat(priceRequest.date).isEqualTo(dateUtils.offsetDateString(dateUtils.today()))
    }
}
