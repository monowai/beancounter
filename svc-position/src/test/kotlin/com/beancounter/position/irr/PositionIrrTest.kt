package com.beancounter.position.irr

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.beancounter.common.model.CashFlow
import com.beancounter.common.model.PeriodicCashFlows
import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.time.LocalDate

/**
 * The IRR a valuation reports for a cash-flow series.
 */
class PositionIrrTest {
    private val positionIrr = PositionIrr(IrrCalculator(minHoldingDays = 365, dateUtils = DateUtils()))
    private val opened = LocalDate.of(2024, 1, 2)
    private val logger = LoggerFactory.getLogger(PositionIrr::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun attachAppender() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun detachAppender() {
        logger.detachAppender(appender)
    }

    @Test
    fun `should report a 0 percent return without a warning`() {
        val irr = positionIrr.of(flows(-100.0, 100.0))

        assertThat(irr).isEqualByComparingTo("0")
        assertThat(appender.list.filter { it.level.isGreaterOrEqual(Level.WARN) }).isEmpty()
    }

    private fun flows(
        invested: Double,
        returned: Double
    ): PeriodicCashFlows =
        PeriodicCashFlows().apply {
            cashFlows.add(CashFlow(invested, opened))
            cashFlows.add(CashFlow(returned, opened.plusDays(30)))
        }
}