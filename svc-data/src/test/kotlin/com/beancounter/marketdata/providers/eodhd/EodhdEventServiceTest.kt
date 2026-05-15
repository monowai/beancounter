package com.beancounter.marketdata.providers.eodhd

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.providers.MarketDataRepo
import com.beancounter.marketdata.providers.eodhd.model.EodhdDividend
import com.beancounter.marketdata.providers.eodhd.model.EodhdSplit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Pure unit tests for [EodhdEventService] — pinned behaviour:
 *  • Markets outside the EODHD allowlist hit the repo fallback, never the gateway.
 *  • Dividend rows surface as MarketData with `dividend>0` and `priceDate=recordDate`
 *    (so downstream CorporateEvent rows use the right key).
 *  • Split rows surface with the parsed factor.
 */
internal class EodhdEventServiceTest {
    private val finder = mock<AssetFinder>()
    private val proxy = mock<EodhdProxy>()
    private val config = mock<EodhdConfig>()
    private val repo = mock<MarketDataRepo>()
    private val service = EodhdEventService(finder, proxy, config, repo)

    private val lon = Market(code = "LON")
    private val nzx = Market(code = "NZX")
    private val barc = Asset(code = "BARC", market = lon, id = "asset-barc")
    private val gne = Asset(code = "GNE", market = nzx, id = "asset-gne")

    @Test
    fun `falls back to repo cache when market is outside the allowlist`() {
        whenever(config.markets).thenReturn("LON")
        whenever(repo.findEventsByAssetId(gne.id)).thenReturn(emptyList())

        service.getEvents(gne)

        verify(repo).findEventsByAssetId(gne.id)
        verify(proxy, never()).getDividends(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        verify(proxy, never()).getSplits(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun `dividend rows carry the recordDate as priceDate, with value as dividend`() {
        whenever(config.markets).thenReturn("LON")
        whenever(config.apiKey).thenReturn("demo")
        whenever(config.getPriceCode(barc)).thenReturn("BARC.LSE")
        whenever(proxy.getDividends("BARC.LSE", "demo")).thenReturn(
            listOf(
                EodhdDividend(
                    date = LocalDate.of(2024, 11, 8),
                    recordDate = LocalDate.of(2024, 11, 11),
                    paymentDate = LocalDate.of(2024, 11, 14),
                    value = BigDecimal("0.25"),
                    currency = "GBP"
                )
            )
        )
        whenever(proxy.getSplits("BARC.LSE", "demo")).thenReturn(emptyList())

        val response = service.getEvents(barc)

        assertThat(response.data).hasSize(1)
        val md = response.data.first()
        assertThat(md.priceDate).isEqualTo(LocalDate.of(2024, 11, 11))
        assertThat(md.dividend).isEqualByComparingTo(BigDecimal("0.25"))
        assertThat(md.split).isEqualByComparingTo(BigDecimal.ONE)
        assertThat(md.source).isEqualTo(EodhdPriceService.ID)
    }

    @Test
    fun `split rows carry the parsed factor`() {
        whenever(config.markets).thenReturn("LON")
        whenever(config.apiKey).thenReturn("demo")
        whenever(config.getPriceCode(barc)).thenReturn("BARC.LSE")
        whenever(proxy.getDividends("BARC.LSE", "demo")).thenReturn(emptyList())
        whenever(proxy.getSplits("BARC.LSE", "demo")).thenReturn(
            listOf(
                EodhdSplit(date = LocalDate.of(2020, 8, 31), split = "4.000000/1.000000")
            )
        )

        val response = service.getEvents(barc)

        assertThat(response.data).hasSize(1)
        val md = response.data.first()
        assertThat(md.priceDate).isEqualTo(LocalDate.of(2020, 8, 31))
        assertThat(md.split).isEqualByComparingTo(BigDecimal("4"))
        assertThat(md.dividend).isEqualByComparingTo(BigDecimal.ZERO)
    }
}