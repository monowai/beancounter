package com.beancounter.marketdata.news.sentiment

import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Pure-unit coverage for [MarketSentimentSchedule]: the cron wrapper delegates to
 * [MarketSentimentService.refresh] and a failed run never escapes to the scheduler thread.
 */
internal class MarketSentimentScheduleTest {
    private val service = mock<MarketSentimentService>()
    private val schedule = MarketSentimentSchedule(service)

    @Test
    fun `refresh delegates to the market sentiment service`() {
        whenever(service.refresh()).thenReturn(MarketSentimentRefreshResult(scopes = 17, rows = 4))

        schedule.refresh()

        verify(service).refresh()
    }

    @Test
    fun `refresh failure is logged and not rethrown`() {
        whenever(service.refresh()).thenThrow(IllegalStateException("database down"))

        assertThatCode { schedule.refresh() }.doesNotThrowAnyException()
    }
}