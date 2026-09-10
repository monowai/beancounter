package com.beancounter.marketdata.macro

import com.beancounter.marketdata.providers.NewsServiceFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Pure-unit coverage for [MacroRefreshSchedule]. Confirms `refresh()` fires all three warm-up
 * steps and that each is wrapped independently — one step's failure must never stop the others.
 * Mirrors [com.beancounter.marketdata.providers.eodhd.news.NewsRetentionScheduleTest].
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
    fun `refresh warms topic news, treasury yields and snapshots rate expectations`() {
        whenever(newsServiceFacade.getTopicNews(any())).thenReturn(emptyMap())
        whenever(treasuryYieldService.getYields()).thenReturn(emptyList())

        schedule.refresh()

        verify(newsServiceFacade).getTopicNews(MacroRefreshSchedule.MACRO_TOPICS)
        verify(treasuryYieldService).getYields()
        verify(rateExpectationsService).snapshot()
    }

    @Test
    fun `a failing news refresh does not stop the treasury yield fetch or the rate snapshot`() {
        whenever(newsServiceFacade.getTopicNews(any())).thenThrow(RuntimeException("EODHD down"))
        whenever(treasuryYieldService.getYields()).thenReturn(emptyList())

        schedule.refresh()

        verify(treasuryYieldService).getYields()
        verify(rateExpectationsService).snapshot()
    }

    @Test
    fun `a failing treasury yield fetch does not stop the news refresh or the rate snapshot`() {
        whenever(newsServiceFacade.getTopicNews(any())).thenReturn(emptyMap())
        whenever(treasuryYieldService.getYields()).thenThrow(RuntimeException("AlphaVantage down"))

        schedule.refresh()

        verify(newsServiceFacade).getTopicNews(any())
        verify(rateExpectationsService).snapshot()
    }

    @Test
    fun `a failing rate expectations snapshot does not stop the news refresh or the treasury fetch`() {
        whenever(newsServiceFacade.getTopicNews(any())).thenReturn(emptyMap())
        whenever(treasuryYieldService.getYields()).thenReturn(emptyList())
        whenever(rateExpectationsService.snapshot()).thenThrow(RuntimeException("Kalshi down"))

        schedule.refresh()

        verify(newsServiceFacade).getTopicNews(any())
        verify(treasuryYieldService).getYields()
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