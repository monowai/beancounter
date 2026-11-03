package com.beancounter.position.irr

import com.beancounter.common.model.CashFlow
import com.beancounter.common.model.PeriodicCashFlows
import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Cash flow patterns with no IRR resolve to zero rather than failing.
 */
class IrrCalculatorEdgeCaseTest {
    private val irrCalculator = IrrCalculator(dateUtils = DateUtils())
    private val start = LocalDate.of(2020, 1, 1)
    private val end = LocalDate.of(2023, 1, 1)

    private fun flows(vararg amounts: Pair<Double, LocalDate>): PeriodicCashFlows {
        val periodic = PeriodicCashFlows()
        periodic.cashFlows.addAll(amounts.map { CashFlow(it.first, it.second) })
        return periodic
    }

    @Test
    fun `should return zero for a single cash flow`() {
        assertThat(irrCalculator.calculate(flows(-1000.0 to start))).isEqualTo(0.0)
    }

    @Test
    fun `should return zero when every cash flow is zero`() {
        assertThat(irrCalculator.calculate(flows(0.0 to start, 0.0 to end))).isEqualTo(0.0)
    }

    @Test
    fun `should return zero when every cash flow is positive`() {
        assertThat(irrCalculator.calculate(flows(1000.0 to start, 500.0 to end))).isEqualTo(0.0)
    }

    @Test
    fun `should return zero when every cash flow is negative`() {
        assertThat(irrCalculator.calculate(flows(-1000.0 to start, -500.0 to end))).isEqualTo(0.0)
    }

    @Test
    fun `should return zero when the first cash flow is zero`() {
        assertThat(irrCalculator.calculate(flows(0.0 to start, 500.0 to end))).isEqualTo(0.0)
    }

    @Test
    fun `should solve the rate for a long holding with a sign change`() {
        val irr = irrCalculator.calculate(flows(-1000.0 to start, 1331.0 to end))

        assertThat(irr).isCloseTo(
            0.1,
            org.assertj.core.data.Offset
                .offset(0.001)
        )
    }
}