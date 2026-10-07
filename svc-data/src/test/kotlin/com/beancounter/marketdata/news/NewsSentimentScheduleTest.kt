package com.beancounter.marketdata.news

import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Pure-unit coverage for [NewsSentimentSchedule]: the cron wrapper delegates to
 * [NewsSentimentService.refresh] and a failed run never escapes to the scheduler thread.
 */
internal class NewsSentimentScheduleTest {
    private val service = mock<NewsSentimentService>()
    private val schedule = NewsSentimentSchedule(service)

    @Test
    fun `refresh delegates to the sentiment service`() {
        whenever(service.refresh()).thenReturn(SentimentRefreshResult(assets = 3, calls = 1, rows = 5))

        schedule.refresh()

        verify(service).refresh()
    }

    @Test
    fun `refresh failure is logged and not rethrown`() {
        whenever(service.refresh()).thenThrow(IllegalStateException("provider down"))

        assertThatCode { schedule.refresh() }.doesNotThrowAnyException()
    }
}