package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.providers.MarketDataPriceProvider
import com.beancounter.marketdata.providers.ProviderUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

/** Prices older than the stored window, asked of the provider and kept nowhere. */
class ProviderHistoryTest {
    private val providerUtils = mock<ProviderUtils>()
    private val assetFinder = mock<AssetFinder>()
    private val provider = mock<MarketDataPriceProvider>()
    private val dateUtils = DateUtils()
    private val history = ProviderHistory(providerUtils, assetFinder, dateUtils)

    private val asset =
        Asset(
            id = "asset-1",
            code = "ABC",
            name = "ABC",
            market = Market("NASDAQ", currencyId = "USD"),
            marketCode = "NASDAQ",
            category = "Equity"
        )
    private val before = LocalDate.of(2016, 11, 18)

    private fun providing(vararg rows: Pair<LocalDate, String>) {
        val inputs = listOf(PriceAsset(asset))
        whenever(assetFinder.findOrNull(asset.id)).thenReturn(asset)
        whenever(providerUtils.getInputs(listOf(asset))).thenReturn(inputs)
        whenever(providerUtils.splitProviders(inputs)).thenReturn(mapOf(provider to mutableListOf(asset)))
        whenever(provider.backFill(any(), any())).thenReturn(
            PriceResponse(rows.map { (date, close) -> MarketData(asset, date, BigDecimal(close)) })
        )
    }

    @Test
    fun `should return only the provider's adjusted closes before the stored window`() {
        whenever(provider.shipsAdjustedClose()).thenReturn(true)
        providing(
            LocalDate.of(2004, 6, 1) to "8",
            LocalDate.of(2010, 6, 1) to "12",
            before to "30",
            before.plusDays(1) to "31"
        )

        assertThat(history.closesBefore(asset.id, before)).containsExactly(
            LocalDate.of(2004, 6, 1) to BigDecimal("8"),
            LocalDate.of(2010, 6, 1) to BigDecimal("12")
        )
    }

    @Test
    fun `should not ask a provider whose closes are not split adjusted`() {
        whenever(provider.shipsAdjustedClose()).thenReturn(false)
        providing(LocalDate.of(2004, 6, 1) to "8")

        assertThat(history.closesBefore(asset.id, before)).isEmpty()
        verify(provider, never()).backFill(any(), any())
    }

    @Test
    fun `should return no closes for an unknown asset`() {
        assertThat(history.closesBefore("missing", before)).isEmpty()
        verify(providerUtils, never()).getInputs(any())
    }
}