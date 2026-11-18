package com.beancounter.marketdata.broker.reconcile

import com.beancounter.marketdata.price.MarketDataRepo
import com.beancounter.marketdata.price.SplitRow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

/** What one share held on a past date has become by the record date. */
class ImportSplitsTest {
    private val marketDataRepo = mock<MarketDataRepo>()
    private val providerHistory = mock<ProviderHistory>()
    private val splits = ImportSplits(marketDataRepo, providerHistory)
    private val assetId = "goog"
    private val from = LocalDate.of(2013, 10, 29)
    private val to = LocalDate.of(2026, 11, 18)

    private fun row(
        date: LocalDate,
        split: String = "1"
    ) = object : SplitRow {
        override val assetId = this@ImportSplitsTest.assetId
        override val priceDate = date
        override val split = BigDecimal(split)
    }

    @Test
    fun `should multiply the stored splits from the trade date up to the record date`() {
        whenever(marketDataRepo.findSplitHistory(listOf(assetId), from, to.minusDays(1))).thenReturn(
            listOf(row(from), row(LocalDate.of(2014, 3, 27), "2"), row(LocalDate.of(2022, 7, 18), "20"))
        )

        assertThat(splits.factor(assetId, from, to)).isEqualByComparingTo("40")
        verify(providerHistory, never()).splitsBetween(any(), any(), any())
    }

    @Test
    fun `should ask the provider for splits older than the stored history`() {
        val storedFrom = LocalDate.of(2016, 11, 18)
        whenever(marketDataRepo.findSplitHistory(listOf(assetId), from, to.minusDays(1))).thenReturn(
            listOf(row(storedFrom), row(LocalDate.of(2022, 7, 18), "20"))
        )
        whenever(providerHistory.splitsBetween(assetId, from, storedFrom))
            .thenReturn(listOf(LocalDate.of(2014, 3, 27) to BigDecimal("2")))

        assertThat(splits.factor(assetId, from, to)).isEqualByComparingTo("40")
    }

    @Test
    fun `should count no split when none is known`() {
        whenever(marketDataRepo.findSplitHistory(listOf(assetId), from, to.minusDays(1))).thenReturn(emptyList())
        whenever(providerHistory.splitsBetween(assetId, from, to)).thenReturn(emptyList())

        assertThat(splits.factor(assetId, from, to)).isEqualByComparingTo("1")
    }

    @Test
    fun `should know no factor when the provider cannot be asked`() {
        whenever(marketDataRepo.findSplitHistory(listOf(assetId), from, to.minusDays(1))).thenReturn(emptyList())
        whenever(providerHistory.splitsBetween(assetId, from, to)).thenThrow(IllegalStateException("provider down"))

        assertThat(splits.factor(assetId, from, to)).isNull()
    }

    @Test
    fun `should ignore a ratio that is not positive`() {
        whenever(marketDataRepo.findSplitHistory(listOf(assetId), from, to.minusDays(1))).thenReturn(
            listOf(
                row(from),
                row(LocalDate.of(2014, 3, 27), "0"),
                row(LocalDate.of(2018, 5, 1), "-3"),
                row(LocalDate.of(2022, 7, 18), "20")
            )
        )

        assertThat(splits.factor(assetId, from, to)).isEqualByComparingTo("20")
    }
}