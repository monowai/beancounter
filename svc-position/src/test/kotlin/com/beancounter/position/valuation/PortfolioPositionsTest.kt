package com.beancounter.position.valuation

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.beancounter.auth.TokenService
import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Positions
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.holdings.PositionService
import com.beancounter.position.utils.TestHelpers
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class PortfolioPositionsTest {
    private val trnService: TrnService = mock()
    private val positionService: PositionService = mock()
    private val earmarkService: EarmarkService = mock()
    private val portfolioPositions =
        PortfolioPositions(trnService, positionService, earmarkService, mock<TokenService>())
    private val portfolios: List<Portfolio> = (1..PORTFOLIOS).map { TestHelpers.createTestPortfolio("pf-$it") }

    init {
        whenever(positionService.build(any(), any())).thenReturn(PositionResponse(Positions()))
    }

    @Test
    fun `reads a large selection a bounded number of portfolios at a time`() {
        val inFlight = AtomicInteger()
        val peak = AtomicInteger()
        whenever(trnService.query(any<Portfolio>(), any<String>())).thenAnswer {
            peak.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
            Thread.sleep(READ_MILLIS)
            inFlight.decrementAndGet()
            TrnResponse(emptyList())
        }

        portfolioPositions.each(portfolios, DateUtils.TODAY)

        assertThat(peak.get()).isGreaterThan(1).isLessThanOrEqualTo(PortfolioPositions.READ_CONCURRENCY)
    }

    @Test
    fun `a failed read fails the aggregate, names the portfolio and starts no further reads`() {
        val calls = AtomicInteger()
        val failed = AtomicBoolean()
        val failedPortfolio = AtomicReference("")
        whenever(trnService.query(any<Portfolio>(), any<String>())).thenAnswer {
            calls.incrementAndGet()
            if (failed.compareAndSet(false, true)) {
                failedPortfolio.set(it.getArgument<Portfolio>(0).code)
                throw BusinessException("bc-data unavailable")
            }
            Thread.sleep(READ_MILLIS)
            TrnResponse(emptyList())
        }
        val logger = LoggerFactory.getLogger(PortfolioPositions::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        val thrown =
            try {
                catchThrowable { portfolioPositions.each(portfolios, DateUtils.TODAY) }
            } finally {
                logger.detachAppender(appender)
            }

        assertThat(thrown).isInstanceOf(BusinessException::class.java).hasMessage("bc-data unavailable")
        assertThat(calls.get()).isLessThanOrEqualTo(PortfolioPositions.READ_CONCURRENCY)
        assertThat(appender.list).anySatisfy {
            assertThat(it.level).isEqualTo(Level.ERROR)
            assertThat(it.formattedMessage).contains(failedPortfolio.get())
        }
    }

    private companion object {
        const val PORTFOLIOS = 20
        const val READ_MILLIS = 100L
    }
}