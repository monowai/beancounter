package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.US
import com.beancounter.marketdata.assets.AssetFinder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The scheduled refresh prices held assets and INDEX benchmarks. Chart overlays
 * (RSP/SPY breadth, "vs SPY") lean on tickers nobody holds and that are not on
 * the INDEX market, so nothing kept their tail current and the overlay silently
 * stopped short of the right edge. `price.refresh.benchmarks` names them.
 */
internal class PriceRefreshBenchmarkTest {
    private val spy = getTestAsset(US, "SPY")
    private val assetFinder = mock<AssetFinder>()
    private val marketDataService = mock<MarketDataService>()
    private val priceService = mock<PriceService>()

    private fun priceRefresh(benchmarks: List<String>): PriceRefresh {
        whenever(assetFinder.findHeldAssetsForPricing()).thenReturn(emptyList())
        whenever(assetFinder.findActiveIndexAssets()).thenReturn(emptyList())
        whenever(priceService.getMarketDataCount(any<String>(), any<LocalDate>())).thenReturn(0L)
        whenever(marketDataService.getPriceResponse(any<PriceRequest>())).thenReturn(
            PriceResponse(listOf(MarketData(spy, close = BigDecimal("767.45"))))
        )
        return PriceRefresh(
            assetFinder,
            marketDataService,
            priceService,
            DateUtils(),
            benchmarks
        )
    }

    @Test
    fun is_ConfiguredBenchmarkPricedWhenNobodyHoldsIt() {
        whenever(assetFinder.findLocally(AssetInput("US", "SPY"))).thenReturn(spy)

        val fetched = priceRefresh(listOf("US:SPY")).updatePrices()

        assertThat(fetched).isEqualTo(1)
        verify(marketDataService).getPriceResponse(any<PriceRequest>())
    }

    @Test
    fun is_UnresolvableBenchmarkSkippedRatherThanFailingTheRun() {
        // A typo in config must not take the whole scheduled refresh down with
        // it — held assets still have to get priced.
        whenever(assetFinder.findLocally(any<AssetInput>())).thenReturn(null)

        val fetched = priceRefresh(listOf("US:NOSUCH")).updatePrices()

        assertThat(fetched).isEqualTo(0)
        verify(marketDataService, never()).getPriceResponse(any<PriceRequest>())
    }

    @Test
    fun is_BenchmarkNotDoubleFetchedWhenAlsoHeld() {
        whenever(assetFinder.findLocally(AssetInput("US", "SPY"))).thenReturn(spy)
        val refresh = priceRefresh(listOf("US:SPY"))
        whenever(assetFinder.findHeldAssetsForPricing()).thenReturn(listOf(spy))

        val fetched = refresh.updatePrices()

        assertThat(fetched).isEqualTo(1)
        verify(marketDataService, times(1)).getPriceResponse(any<PriceRequest>())
    }

    @Test
    fun is_UnsetBenchmarkConfigLeavesRefreshUntouched() {
        // Spring binds an unset `price.refresh.benchmarks` as a single empty
        // string. That must resolve to no benchmarks and no complaint.
        val fetched = priceRefresh(listOf("")).updatePrices()

        assertThat(fetched).isEqualTo(0)
        verify(assetFinder, never()).findLocally(any<AssetInput>())
    }

    @Test
    fun is_MarketCodeParsedCaseInsensitively() {
        whenever(assetFinder.findLocally(eq(AssetInput("US", "SPY")))).thenReturn(spy)

        val fetched = priceRefresh(listOf("us:spy")).updatePrices()

        assertThat(fetched).isEqualTo(1)
    }
}