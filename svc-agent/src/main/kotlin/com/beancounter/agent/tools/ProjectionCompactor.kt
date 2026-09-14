package com.beancounter.agent.tools

import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Shrinks svc-retire's projection payloads before they enter the LLM conversation
 * as tool results.
 *
 * Why this exists, concretely: on kauri an Independence question reached
 * `prompt_tokens=116608` against a ~128k window, spent the remaining budget on tool
 * calls and reasoning, and finished on `length` having produced no answer at all —
 * the user saw an empty chat bubble. A single [runRetirementProjection] response
 * carries three year-by-year arrays (`yearlyProjections`, `accumulationProjections`,
 * `firePathProjections`) of ~31-field rows running to life expectancy, and a
 * scenarios run repeats that per scenario.
 *
 * Three reductions, all lossless about *what the answer needs*:
 *
 *  1. **Columnar** — `{cols, rows}` instead of an array of objects, so a field name
 *     is paid for once rather than once per year (the same trick
 *     [ScrubbedPositionResponse] already uses for positions).
 *  2. **Sampled** — at most [MAX_ROWS] evenly-spaced rows, always including the
 *     first and last. A projection's shape and its endpoints answer "how does my
 *     plan look"; year 27 of 40 in isolation does not. The row count it was
 *     sampled from is reported as `sampledFrom` so the model never mistakes the
 *     sample for the whole horizon.
 *  3. **Empty columns dropped** — a column that is null or zero in every sampled
 *     row is removed and named under `emptyCols`. Named, not silently dropped:
 *     "you have no rental income" and "I wasn't shown rental income" are different
 *     answers, and only the first one is true.
 *
 * Scalars, short arrays and arrays of scalars pass through untouched, so the
 * headline metrics the LLM actually quotes (`depletionAge`, `runwayYears`,
 * `fiAchievementAge`, …) are never touched.
 */
@Service
class ProjectionCompactor {
    /** Compact every value in a tool-result payload, recursively. */
    fun compact(payload: Map<String, Any?>): Map<String, Any?> = payload.mapValues { (_, value) -> compactValue(value) }

    private fun compactValue(value: Any?): Any? =
        when (value) {
            is Map<*, *> -> value.entries.associate { (k, v) -> k to compactValue(v) }
            is List<*> -> compactList(value)
            else -> value
        }

    /**
     * Tabulate only what is worth tabulating: a run of like-shaped objects long
     * enough for the key repetition to cost real tokens. Anything else — scalars,
     * a mixed bag, a handful of warnings — recurses and is otherwise left as it
     * was, because reshaping it would cost the model clarity and save nothing.
     */
    private fun compactList(list: List<*>): Any {
        val rows = list.filterIsInstance<Map<*, *>>()
        if (list.size < MIN_ROWS || rows.size != list.size) {
            return list.map(::compactValue)
        }
        return tabulate(rows)
    }

    private fun tabulate(rows: List<Map<*, *>>): Map<String, Any?> {
        val sampled = sample(rows)
        val allCols = rows.flatMap { row -> row.keys.map { it.toString() } }.distinct()
        val emptyCols = allCols.filter { col -> sampled.all { row -> isEmpty(row[col]) } }
        // Everything empty means the sample says nothing at all; keep the raw
        // columns rather than emitting a table with no columns in it.
        val cols = (allCols - emptyCols.toSet()).ifEmpty { allCols }

        return buildMap {
            put("cols", cols)
            put("rows", sampled.map { row -> cols.map { col -> compactValue(row[col]) } })
            if (sampled.size < rows.size) put("sampledFrom", rows.size)
            if (cols !== allCols && emptyCols.isNotEmpty()) put("emptyCols", emptyCols)
        }
    }

    /**
     * At most [MAX_ROWS] rows, evenly spaced across the input and always including
     * both endpoints — the start and the end of a projection are the two rows
     * every question about it turns on.
     */
    private fun sample(rows: List<Map<*, *>>): List<Map<*, *>> {
        if (rows.size <= MAX_ROWS) return rows
        val step = (rows.size - 1).toDouble() / (MAX_ROWS - 1)
        return (0 until MAX_ROWS).map { i -> rows[Math.round(i * step).toInt()] }
    }

    /** Null, numeric zero, or an empty collection — nothing a reader could learn from. */
    private fun isEmpty(value: Any?): Boolean =
        when (value) {
            null -> true
            is BigDecimal -> value.signum() == 0
            is Number -> value.toDouble() == 0.0
            is Collection<*> -> value.isEmpty()
            is Map<*, *> -> value.isEmpty()
            else -> false
        }

    companion object {
        /**
         * Shortest array worth tabulating. Below this the columnar form saves
         * little and reads worse than the objects it replaced.
         */
        const val MIN_ROWS = 6

        /**
         * Rows kept from a sampled array. Twelve spans a 40-year horizon at
         * roughly 3-4 year intervals — enough to see the trajectory and its
         * turning points without paying for every year of it.
         */
        const val MAX_ROWS = 12
    }
}