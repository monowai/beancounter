package com.beancounter.marketdata.macro

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Unit tests for [TreasuryYieldFetcher] — the cached FRED `fredgraph.csv` parse layer shared by
 * [TreasuryYieldService] and [MacroRefreshSchedule].
 */
class TreasuryYieldFetcherTest {
    private val today = LocalDate.of(2026, 10, 7)

    private fun fetcherReturning(csv: String): Pair<TreasuryYieldFetcher, FredGateway> {
        val gateway = mock<FredGateway>()
        whenever(gateway.getSeriesCsv(any(), any())).thenReturn(csv)
        val dateUtils = mock<DateUtils>()
        whenever(dateUtils.date).thenReturn(today)
        return TreasuryYieldFetcher(gateway, dateUtils) to gateway
    }

    @Test
    fun `fetch requests the given FRED series id bounded to the lookback window`() {
        val (fetcher, gateway) = fetcherReturning("observation_date,DGS10\n")

        fetcher.fetch("DGS10")

        verify(gateway).getSeriesCsv("DGS10", today.minusDays(TreasuryYieldFetcher.WINDOW_DAYS))
    }

    @Test
    fun `points are returned newest first with non-trading-day dot rows filtered out`() {
        val csv =
            """
            observation_date,DGS10
            2026-09-06,4.79
            2026-09-07,.
            2026-09-09,4.83
            """.trimIndent()
        val (fetcher, _) = fetcherReturning(csv)

        val points = fetcher.fetch("DGS10")

        assertThat(points.map { it.date }).containsExactly(
            LocalDate.of(2026, 9, 9),
            LocalDate.of(2026, 9, 6)
        )
        assertThat(points.first().value).isEqualByComparingTo(BigDecimal("4.83"))
    }

    @Test
    fun `blank upstream response returns an empty list rather than throwing`() {
        val (fetcher, _) = fetcherReturning("")

        assertThat(fetcher.fetch("DGS2")).isEmpty()
    }

    @Test
    fun `header-only upstream response returns an empty list`() {
        val (fetcher, _) = fetcherReturning("observation_date,DGS2\n")

        assertThat(fetcher.fetch("DGS2")).isEmpty()
    }

    @Test
    fun `malformed upstream body returns an empty list rather than throwing`() {
        val (fetcher, _) = fetcherReturning("<html>rate limited</html>\nnot,a,date\n2026-13-45,4.1\n2026-09-09,abc")

        assertThat(fetcher.fetch("DGS2")).isEmpty()
    }

    @Test
    fun `non-blank body with no parseable rows is logged as a warning`() {
        val (fetcher, _) = fetcherReturning("<html>rate limited</html>")
        val logger = LoggerFactory.getLogger(TreasuryYieldFetcher::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            fetcher.fetch("DGS2")
        } finally {
            logger.detachAppender(appender)
        }

        assertThat(appender.list)
            .anySatisfy {
                assertThat(it.level).isEqualTo(Level.WARN)
                assertThat(it.formattedMessage).contains("DGS2").contains("no parseable rows")
            }
    }

    @Test
    fun `malformed rows are skipped without dropping the well-formed ones`() {
        val csv =
            """
            observation_date,DGS10
            2026-09-09,4.83
            garbage line without comma
            2026-09-08
            """.trimIndent()
        val (fetcher, _) = fetcherReturning(csv)

        val points = fetcher.fetch("DGS10")

        assertThat(points).hasSize(1)
        assertThat(points.single().date).isEqualTo(LocalDate.of(2026, 9, 9))
    }
}