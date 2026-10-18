package com.beancounter.agent.tools

import com.beancounter.common.contracts.PricePoint
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * [Momentum] reads trend and relative strength off a split-adjusted daily close series.
 */
class MomentumTest {
    private val today = LocalDate.parse("2026-10-16")

    // One close per calendar day, oldest first, rising by one each day from 100.
    private fun rising(days: Int): List<PricePoint> =
        (days - 1 downTo 0).mapIndexed { i, back ->
            PricePoint(priceDate = today.minusDays(back.toLong()), close = BigDecimal(100 + i))
        }

    @Test
    fun `should measure returns back to the last close on or before each lookback date`() {
        val series = rising(400)
        val latest = 100.0 + 399

        val momentum = Momentum.of(series)

        // 2026-09-16 is 30 days back, so its close is 399 - 30 + 100.
        assertThat(momentum.return1m).isCloseTo(latest / 469.0 - 1, within(0.0001))
        assertThat(momentum.return3m).isCloseTo(latest / (latest - 92) - 1, within(0.0001))
        assertThat(momentum.return12m).isCloseTo(latest / (latest - 365) - 1, within(0.0001))
    }

    @Test
    fun `should leave a return empty when the series does not reach back that far`() {
        val momentum = Momentum.of(rising(60))

        assertThat(momentum.return1m).isNotNull()
        assertThat(momentum.return6m).isNull()
        assertThat(momentum.return12m).isNull()
    }

    @Test
    fun `should place the latest close against its 50 and 200 day averages`() {
        val momentum = Momentum.of(rising(250))
        val latest = 349.0

        // Average of the last 50 closes, 300..349, is 324.5.
        assertThat(momentum.vsSma50).isCloseTo(latest / 324.5 - 1, within(0.0001))
        // Average of the last 200 closes, 150..349, is 249.5.
        assertThat(momentum.vsSma200).isCloseTo(latest / 249.5 - 1, within(0.0001))
    }

    @Test
    fun `should leave an average empty with too few closes`() {
        val momentum = Momentum.of(rising(120))

        assertThat(momentum.vsSma50).isNotNull()
        assertThat(momentum.vsSma200).isNull()
    }

    @Test
    fun `should measure distance from the 52 week high and low`() {
        val peak =
            rising(300).map {
                if (it.priceDate ==
                    today.minusDays(100)
                ) {
                    it.copy(close = BigDecimal(500))
                } else {
                    it
                }
            }
        val old = PricePoint(priceDate = today.minusDays(400), close = BigDecimal(900))

        val momentum = Momentum.of(listOf(old) + peak)

        // The 900 close is older than a year, so the high is the 500 peak.
        assertThat(momentum.fromHigh52w).isCloseTo(399.0 / 500 - 1, within(0.0001))
        assertThat(momentum.fromLow52w).isCloseTo(399.0 / 100 - 1, within(0.0001))
    }

    @Test
    fun `should report nothing for an empty series`() {
        val momentum = Momentum.of(emptyList())

        assertThat(momentum.asOf).isNull()
        assertThat(momentum.return1m).isNull()
        assertThat(momentum.vsSma50).isNull()
        assertThat(momentum.fromHigh52w).isNull()
    }

    @Test
    fun `should sort the series before reading the latest close`() {
        val momentum = Momentum.of(rising(60).reversed())

        assertThat(momentum.asOf).isEqualTo("2026-10-16")
    }
}