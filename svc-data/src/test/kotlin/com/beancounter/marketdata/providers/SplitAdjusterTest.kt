package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PricePoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class SplitAdjusterTest {
    private fun point(
        date: String,
        close: Double,
        split: Double = 1.0,
        previousClose: Double = 0.0,
        open: Double = 0.0,
        high: Double = 0.0,
        low: Double = 0.0
    ) = PricePoint(
        priceDate = LocalDate.parse(date),
        close = BigDecimal.valueOf(close),
        open = BigDecimal.valueOf(open),
        high = BigDecimal.valueOf(high),
        low = BigDecimal.valueOf(low),
        previousClose = BigDecimal.valueOf(previousClose),
        split = BigDecimal.valueOf(split)
    )

    @Test
    fun `passes through when no splits exist`() {
        val prices = listOf(point("2026-04-01", 100.0), point("2026-04-02", 101.0))
        val adjusted = SplitAdjuster.adjust(prices)
        assertThat(adjusted).isEqualTo(prices)
    }

    @Test
    fun `divides pre-split rows by the ex-date factor`() {
        val prices =
            listOf(
                point("2026-04-03", 5000.0),
                point("2026-04-06", 200.0, split = 25.0),
                point("2026-04-07", 205.0)
            )
        val adjusted = SplitAdjuster.adjust(prices)
        assertThat(adjusted[0].close).isEqualByComparingTo(BigDecimal("200"))
        assertThat(adjusted[1].close).isEqualByComparingTo(BigDecimal("200"))
        assertThat(adjusted[2].close).isEqualByComparingTo(BigDecimal("205"))
    }

    @Test
    fun `treats consecutive non-1 split rows as one event (VO regression)`() {
        // VO 4:1 split on 2026-04-21. Alpha enricher previously stamped the
        // split on 2026-04-22 too. The adjuster must collapse the duplicate
        // stamp into one ex-date event so pre-event rows are divided once.
        val prices =
            listOf(
                point("2026-04-20", 308.395),
                point("2026-04-21", 76.625, split = 4.0),
                point("2026-04-22", 76.56, split = 4.0),
                point("2026-04-23", 76.825),
                point("2026-04-24", 76.71)
            )

        val adjusted = SplitAdjuster.adjust(prices)

        // 308.395 / 4 = 77.09875
        assertThat(adjusted[0].close).isEqualByComparingTo(BigDecimal("77.09875"))
        assertThat(adjusted[1].close).isEqualByComparingTo(BigDecimal("76.625"))
        assertThat(adjusted[2].close).isEqualByComparingTo(BigDecimal("76.56"))
        assertThat(adjusted[3].close).isEqualByComparingTo(BigDecimal("76.825"))

        // Sticky neighbour split is normalised back to 1; only the canonical
        // ex-date keeps its non-1 marker.
        assertThat(adjusted[1].split).isEqualByComparingTo(BigDecimal("4"))
        assertThat(adjusted[2].split).isEqualByComparingTo(BigDecimal.ONE)
    }

    @Test
    fun `compounds factors across multiple split events`() {
        val prices =
            listOf(
                point("2026-01-01", 1000.0),
                point("2026-02-01", 500.0, split = 2.0),
                point("2026-02-15", 510.0),
                point("2026-03-01", 102.0, split = 5.0),
                point("2026-03-02", 103.0)
            )
        val adjusted = SplitAdjuster.adjust(prices)
        // First row sees both events: 1000 / (2 * 5) = 100
        assertThat(adjusted[0].close).isEqualByComparingTo(BigDecimal("100"))
        // Between events: 500 and 510 see only the 5:1 → /5
        assertThat(adjusted[1].close).isEqualByComparingTo(BigDecimal("100"))
        assertThat(adjusted[2].close).isEqualByComparingTo(BigDecimal("102"))
        // Ex-date and after: untouched
        assertThat(adjusted[3].close).isEqualByComparingTo(BigDecimal("102"))
        assertThat(adjusted[4].close).isEqualByComparingTo(BigDecimal("103"))
    }

    @Test
    fun `also adjusts open high low and previousClose`() {
        val prices =
            listOf(
                point(
                    "2026-04-03",
                    close = 5000.0,
                    open = 4900.0,
                    high = 5100.0,
                    low = 4800.0,
                    previousClose = 4950.0
                ),
                point("2026-04-06", 200.0, split = 25.0)
            )
        val adjusted = SplitAdjuster.adjust(prices)
        assertThat(adjusted[0].open).isEqualByComparingTo(BigDecimal("196"))
        assertThat(adjusted[0].high).isEqualByComparingTo(BigDecimal("204"))
        assertThat(adjusted[0].low).isEqualByComparingTo(BigDecimal("192"))
        assertThat(adjusted[0].previousClose).isEqualByComparingTo(BigDecimal("198"))
    }

    @Test
    fun `leaves zero-valued OHLC fields alone`() {
        val prices =
            listOf(
                point("2026-04-03", close = 5000.0), // open/high/low default 0
                point("2026-04-06", 200.0, split = 25.0)
            )
        val adjusted = SplitAdjuster.adjust(prices)
        assertThat(adjusted[0].open).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(adjusted[0].high).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(adjusted[0].low).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `empty input returns empty`() {
        assertThat(SplitAdjuster.adjust(emptyList())).isEmpty()
    }
}