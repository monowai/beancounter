package com.beancounter.event.backfill

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.beancounter.common.model.Portfolio
import com.beancounter.event.utils.TestHelpers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A background load runs one portfolio's load on the dedicated executor: the caller
 * returns at once, a second start for a running portfolio is refused, and a failure
 * is logged and frees the portfolio for the next start.
 */
class BackgroundEventLoadTest {
    private val eventLoader = mock<RangeEventLoader>()
    private val executor = EventLoadExecutor(Executors.newSingleThreadExecutor())
    private val backgroundLoad = BackgroundEventLoad(eventLoader, executor)
    private val portfolio = TestHelpers.createTestPortfolio()
    private val release = CountDownLatch(1)
    private val finished = CountDownLatch(1)
    private val logger = LoggerFactory.getLogger(BackgroundEventLoad::class.java) as Logger
    private val logged = ListAppender<ILoggingEvent>().apply { start() }

    @AfterEach
    fun closeExecutor() {
        release.countDown()
        executor.close()
        logger.detachAppender(logged)
    }

    @Test
    fun `should return STARTED before the load finishes`() {
        loadBlocksUntilReleased()

        val status = backgroundLoad.start(portfolio, AS_AT)

        assertThat(status).isEqualTo(BackgroundLoadStatus.STARTED)
        assertThat(finished.count).isEqualTo(1)
        release.countDown()
        assertThat(finished.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
        verify(eventLoader).load(portfolio, AS_AT)
    }

    @Test
    fun `should not start a second load while the portfolio's load is running`() {
        loadBlocksUntilReleased()

        backgroundLoad.start(portfolio, AS_AT)
        val second = backgroundLoad.start(portfolio, AS_AT)

        assertThat(second).isEqualTo(BackgroundLoadStatus.ALREADY_RUNNING)
        release.countDown()
        assertThat(finished.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
        verify(eventLoader, times(1)).load(portfolio, AS_AT)
    }

    @Test
    fun `should start again once the previous load has finished`() {
        release.countDown()
        loadBlocksUntilReleased()
        backgroundLoad.start(portfolio, AS_AT)
        assertThat(finished.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue()

        awaitIdle()
        assertThat(backgroundLoad.start(portfolio, AS_AT)).isEqualTo(BackgroundLoadStatus.STARTED)
    }

    @Test
    fun `should log a failed load at error with the portfolio and free the slot`() {
        val failed = CountDownLatch(1)
        doAnswer {
            failed.countDown()
            error("provider down")
        }.whenever(eventLoader).load(any<Portfolio>(), any())
        logger.addAppender(logged)

        backgroundLoad.start(portfolio, AS_AT)
        assertThat(failed.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
        awaitIdle()

        assertThat(logged.list.filter { it.level == Level.ERROR }.map { it.formattedMessage })
            .anyMatch { it.contains(portfolio.id) }
        assertThat(backgroundLoad.isRunning(portfolio.id)).isFalse()
    }

    private fun loadBlocksUntilReleased() {
        doAnswer {
            release.await(WAIT_SECONDS, TimeUnit.SECONDS)
            finished.countDown()
        }.whenever(eventLoader).load(any<Portfolio>(), any())
    }

    /** The slot is freed after the load returns, so wait for the executor's task to end. */
    private fun awaitIdle() {
        val idle = CountDownLatch(1)
        executor.execute { idle.countDown() }
        assertThat(idle.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
    }

    private companion object {
        val AS_AT: LocalDate = LocalDate.of(2024, 1, 15)
        const val WAIT_SECONDS = 5L
    }
}