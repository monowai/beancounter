package com.beancounter.agent.tools

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * [ProjectionCompactor] shrinks svc-retire's raw projection payloads before they
 * become tool results in the LLM conversation.
 *
 * The problem it solves is measured, not theoretical: an Independence question on
 * kauri filled the context window (prompt_tokens=116608) and the model finished on
 * `length` with no answer at all. A single projection carries three year-by-year
 * arrays of ~31-field rows, and a scenarios run carries one set per scenario.
 */
internal class ProjectionCompactorTest {
    private val compactor = ProjectionCompactor()

    private fun year(
        index: Int,
        extra: Map<String, Any?> = emptyMap()
    ): Map<String, Any?> =
        mapOf(
            "year" to 2026 + index,
            "age" to 61 + index,
            "endingBalance" to BigDecimal(1000 - index),
            "lumpSumPayout" to BigDecimal.ZERO,
            "rentalIncome" to null
        ) + extra

    private fun years(count: Int): List<Map<String, Any?>> = (0 until count).map { year(it) }

    @Test
    fun `columnarises a long homogeneous array into cols plus positional rows`() {
        val compact = compactor.compact(mapOf("yearlyProjections" to years(20)))

        val table = compact["yearlyProjections"] as Map<*, *>
        assertThat(table["cols"] as List<*>).contains("year", "age", "endingBalance")
        val rows = table["rows"] as List<*>
        assertThat(rows).isNotEmpty
        assertThat(rows.first() as List<*>).hasSameSizeAs(table["cols"] as List<*>)
    }

    @Test
    fun `samples a long array down to a readable number of rows and says so`() {
        val compact = compactor.compact(mapOf("yearlyProjections" to years(40)))

        val table = compact["yearlyProjections"] as Map<*, *>
        val rows = table["rows"] as List<*>
        assertThat(rows.size).isLessThanOrEqualTo(ProjectionCompactor.MAX_ROWS)
        // The model must know it is reading a sample, or it will state the
        // sampled row count as fact ("your plan covers 12 years").
        assertThat(table["sampledFrom"]).isEqualTo(40)
    }

    @Test
    fun `keeps the first and last row of a sampled array`() {
        // The endpoints carry the two facts a projection question turns on: where
        // the plan starts and where it ends up.
        val compact = compactor.compact(mapOf("yearlyProjections" to years(40)))

        val table = compact["yearlyProjections"] as Map<*, *>
        val cols = table["cols"] as List<*>
        val rows = table["rows"] as List<*>
        val ageAt = cols.indexOf("age")
        assertThat((rows.first() as List<*>)[ageAt]).isEqualTo(61)
        assertThat((rows.last() as List<*>)[ageAt]).isEqualTo(61 + 39)
    }

    @Test
    fun `drops columns that are null or zero in every sampled row and names them`() {
        val compact = compactor.compact(mapOf("yearlyProjections" to years(20)))

        val table = compact["yearlyProjections"] as Map<*, *>
        val cols = table["cols"] as List<*>
        assertThat(cols).doesNotContain("lumpSumPayout", "rentalIncome")
        // Named rather than silently vanished: "absent" and "zero throughout"
        // are different answers to "do I have rental income?".
        assertThat(table["emptyCols"] as List<*>).contains("lumpSumPayout", "rentalIncome")
    }

    @Test
    fun `keeps a column that is non-zero in any sampled row`() {
        val rows = years(20).mapIndexed { i, row -> if (i == 5) row + ("lumpSumPayout" to BigDecimal(250)) else row }

        val compact = compactor.compact(mapOf("yearlyProjections" to rows))

        val table = compact["yearlyProjections"] as Map<*, *>
        // Only defensible if the value survives sampling — row 5 is retained by
        // the even-spacing rule for a 20-row input.
        assertThat(table["cols"] as List<*>).contains("lumpSumPayout")
    }

    @Test
    fun `leaves a short array alone`() {
        val warnings = listOf(mapOf("code" to "NO_EXPENSES"), mapOf("code" to "VALUATION_UNAVAILABLE"))

        val compact = compactor.compact(mapOf("warnings" to warnings))

        assertThat(compact["warnings"]).isEqualTo(warnings)
    }

    @Test
    fun `leaves scalars and arrays of scalars untouched`() {
        val raw =
            mapOf(
                "planId" to "plan-1",
                "depletionAge" to 87,
                "currency" to "NZD",
                "tags" to listOf("a", "b", "c", "d", "e", "f", "g", "h")
            )

        assertThat(compactor.compact(raw)).isEqualTo(raw)
    }

    @Test
    fun `compacts arrays nested inside scenario objects`() {
        // runRetirementScenarios returns one projection per scenario — the
        // multiplier that turned a heavy payload into an unanswerable one.
        val raw =
            mapOf(
                "scenarios" to
                    listOf(
                        mapOf("name" to "base", "projections" to years(30)),
                        mapOf("name" to "stress", "projections" to years(30))
                    )
            )

        val compact = compactor.compact(raw)

        val scenarios = compact["scenarios"] as List<*>
        val first = scenarios.first() as Map<*, *>
        assertThat(first["name"]).isEqualTo("base")
        assertThat((first["projections"] as Map<*, *>)["sampledFrom"]).isEqualTo(30)
    }

    @Test
    fun `leaves a heterogeneous array alone rather than inventing columns`() {
        val mixed = listOf(mapOf("a" to 1), "loose string", mapOf("a" to 2), 4, 5, 6, 7)

        assertThat(compactor.compact(mapOf("mixed" to mixed))["mixed"]).isEqualTo(mixed)
    }

    @Test
    fun `a compacted projection is a fraction of the raw payload`() {
        // The whole point, asserted end-to-end: rendered size, not row count.
        val raw = mapOf("yearlyProjections" to years(40))

        val rawSize = raw.toString().length
        val compactSize = compactor.compact(raw).toString().length

        assertThat(compactSize).isLessThan(rawSize / 4)
    }
}