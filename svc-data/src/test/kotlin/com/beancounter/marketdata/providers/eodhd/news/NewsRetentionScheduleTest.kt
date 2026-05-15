package com.beancounter.marketdata.providers.eodhd.news

import com.beancounter.marketdata.providers.eodhd.EodhdNewsProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Pure-unit coverage for [NewsRetentionSchedule]. Confirms `prune()` deletes everything published
 * before `now - retentionDays` — the only thing the @Scheduled wrapping is meant to invoke.
 *
 * The Spring `@Scheduled` registration itself is exercised by Spring Boot integration tests;
 * here we just pin the cutoff arithmetic and the repo call so the prune doesn't drift.
 */
internal class NewsRetentionScheduleTest {
    private val repo = mock<NewsArticleRepo>()
    private val props = EodhdNewsProperties(retentionDays = 30)
    private val schedule = NewsRetentionSchedule(repo, props)

    @Test
    fun `prune calls deleteByPublishedBefore with retentionDays cutoff`() {
        whenever(repo.deleteByPublishedBefore(org.mockito.kotlin.any())).thenReturn(7)

        schedule.prune()

        val captor = argumentCaptor<LocalDateTime>()
        verify(repo).deleteByPublishedBefore(captor.capture())
        // Cutoff must be 30 days back (± a couple seconds for test clock drift).
        val expected = LocalDateTime.now().minusDays(30)
        val delta = ChronoUnit.SECONDS.between(captor.firstValue, expected)
        assertThat(delta).isBetween(-5, 5)
    }

    @Test
    fun `prune honours a custom retentionDays override`() {
        val custom = NewsRetentionSchedule(repo, EodhdNewsProperties(retentionDays = 7))
        whenever(repo.deleteByPublishedBefore(org.mockito.kotlin.any())).thenReturn(0)

        custom.prune()

        val captor = argumentCaptor<LocalDateTime>()
        verify(repo).deleteByPublishedBefore(captor.capture())
        val expected = LocalDateTime.now().minusDays(7)
        val delta = ChronoUnit.SECONDS.between(captor.firstValue, expected)
        assertThat(delta).isBetween(-5, 5)
    }
}