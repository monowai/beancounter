package com.beancounter.marketdata.price

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.US
import com.beancounter.marketdata.assets.AssetFinder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal

/**
 * A single-asset refresh fetches the asset's price before it reads the stored price back, so the
 * response carries the price the refresh just stored.
 */
internal class PriceRefreshSingleAssetTest {
    private val asset = getTestAsset(US, "ABC")
    private val stored = mutableListOf<MarketData>()

    private val assetFinder = mock<AssetFinder>().also { whenever(it.find(asset.id)).thenReturn(asset) }
    private val utilityService =
        mock<MarketDataUtilityService> {
            on { refresh(any(), any()) } doAnswer {
                stored.add(MarketData(asset, close = BigDecimal("12.34")))
                Unit
            }
        }
    private val marketDataService =
        mock<MarketDataService> {
            on { getPriceResponse(any<PriceRequest>()) } doAnswer { PriceResponse(stored.toList()) }
        }
    private val priceRefresh =
        PriceRefresh(assetFinder, marketDataService, utilityService, mock<PriceService>(), DateUtils())

    @Test
    fun `refreshing one asset returns the price the refresh stored`() {
        val response = priceRefresh.refreshPrice(asset.id, "2024-01-02")

        assertThat(response.data.map { it.close }).containsExactly(BigDecimal("12.34"))
    }
}