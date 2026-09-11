package com.beancounter.marketdata.macro

import com.beancounter.marketdata.news.NewsServiceFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Pure-unit coverage for [MacroRefreshSchedule]. Confirms `refresh()` fires all four warm-up /
 * housekeeping steps and that each is wrapped independently — one step's failure must never stop
 * the others. Mirrors [com.beancounter.marketdata.news.NewsRetentionScheduleTest].
 *
 * [MacroRefreshSchedule] depends only on [NewsServiceFacade]'s public `NewsProvider` surface, not
 * any EODHD-specific class — the topic-news feature may move into its own service later without
 * touching this schedule.
 */
internal class MacroRefreshScheduleTest {
    private val newsServiceFacade = mock<NewsServiceFacade>()
    private val treasuryYieldService = mock<TreasuryYieldService>()
    private val rateExpectationsService = mock<RateExpectationsService>()
    private val schedule = MacroRefreshSchedule(newsServiceFacade, treasuryYieldService, rateExpectationsService)

    @Test
    fun `refresh warms topic news, treasury yields, snapshots rate expectations and prunes old observations`() {
        whenever(newsServiceFacade.getTopicNews(any())).thenReturn(emptyMap())
        whenever(treasuryYieldService.getYields()).thenReturn(emptyList())

        schedule.refresh()

        verify(newsServiceFacade).getTopicNews(MacroRefreshSchedule.MACRO_TOPICS)
        verify(treasuryYieldService).getYields()
        verify(rateExpectationsService).snapshot()
        // Default MacroScheduleProperties().retentionDays == 90.
        verify(rateExpectationsService).prune(90)
    }

    @Test
    fun `a failing news refresh does not stop the treasury yield fetch, the rate snapshot or the prune`() {
        whenever(newsServiceFacade.getTopicNews(any())).thenThrow(RuntimeException("EODHD down"))
        whenever(treasuryYieldService.getYields()).thenReturn(emptyList())

        schedule.refresh()

        verify(treasuryYieldService).getYields()
        verify(rateExpectationsService).snapshot()
        verify(rateExpectationsService).prune(90)
    }

    @Test
    fun `a failing treasury yield fetch does not stop the news refresh, the rate snapshot or the prune`() {
        whenever(newsServiceFacade.getTopicNews(any())).thenReturn(emptyMap())
        whenever(treasuryYieldService.getYields()).thenThrow(RuntimeException("AlphaVantage down"))

        schedule.refresh()

        verify(newsServiceFacade).getTopicNews(any())
        verify(rateExpectationsService).snapshot()
        verify(rateExpectationsService).prune(90)
    }

    @Test
    fun `a failing rate expectations snapshot does not stop the news refresh, the treasury fetch or the prune`() {
        whenever(newsServiceFacade.getTopicNews(any())).thenReturn(emptyMap())
        whenever(treasuryYieldService.getYields()).thenReturn(emptyList())
        whenever(rateExpectationsService.snapshot()).thenThrow(RuntimeException("Kalshi down"))

        schedule.refresh()

        verify(newsServiceFacade).getTopicNews(any())
        verify(treasuryYieldService).getYields()
        verify(rateExpectationsService).prune(90)
    }

    @Test
    fun `a failing prune does not stop the news refresh, the treasury fetch or the rate snapshot`() {
        whenever(newsServiceFacade.getTopicNews(any())).thenReturn(emptyMap())
        whenever(treasuryYieldService.getYields()).thenReturn(emptyList())
        whenever(rateExpectationsService.prune(any())).thenThrow(RuntimeException("DB down"))

        schedule.refresh()

        verify(newsServiceFacade).getTopicNews(any())
        verify(treasuryYieldService).getYields()
        verify(rateExpectationsService).snapshot()
    }

    @Test
    fun `a custom retentionDays override is passed through to the prune step`() {
        val custom =
            MacroRefreshSchedule(
                newsServiceFacade,
                treasuryYieldService,
                rateExpectationsService,
                MacroScheduleProperties(retentionDays = 30)
            )
        whenever(newsServiceFacade.getTopicNews(any())).thenReturn(emptyMap())
        whenever(treasuryYieldService.getYields()).thenReturn(emptyList())

        custom.refresh()

        verify(rateExpectationsService).prune(30)
    }

    @Test
    fun `MACRO_TOPICS covers the documented macro topic vocabulary`() {
        assertThat(MacroRefreshSchedule.MACRO_TOPICS).containsExactly(
            "stock markets",
            "economy",
            "inflation",
            "bonds",
            "energy",
            "commodities"
        )
    }
}