package com.beancounter.agent.tools

import com.beancounter.common.model.SentimentBands
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Compresses svc-data's daily sentiment rows into a this-week against last-week comparison, so a
 * review reads two small totals per series instead of a month of daily points. Pure; no I/O.
 *
 * Both windows end on the newest day present in the data, not on the wall clock — the result
 * carries that day as `asOf`, so stale data shows as stale rather than as a quiet week.
 *
 * The output is written for a reader, not a statistician: article counts, whole percentages and
 * worded `tone` and `direction`. The balance and score they are derived from stay internal, so a
 * review cannot quote a figure like "net 0.42" that means nothing to the user.
 */
internal object SentimentTrend {
    const val WINDOW_DAYS = 7L
    const val FETCH_DAYS = 14

    /**
     * One entry per series in svc-data's `/news/sentiment/market` payload, split into `topics` and
     * `sectors`. Empty when no series carries a point.
     */
    fun market(raw: Map<String, Any>): Map<String, Any> {
        val series = (raw["data"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        val asOf = series.flatMap { points(it["points"]) }.maxOfOrNull { it.date } ?: return emptyMap()
        val summaries =
            series.mapNotNull { one ->
                val counts = points(one["points"])
                val latest = counts.filter { it.date > asOf.minusDays(WINDOW_DAYS) }
                val prior = counts.filter { it.date <= asOf.minusDays(WINDOW_DAYS) }
                if (latest.isEmpty() && prior.isEmpty()) {
                    null
                } else {
                    one["kind"].toString() to
                        compare(countTotals(latest), countTotals(prior)) + ("name" to one["name"].toString())
                }
            }
        return mapOf(
            "asOf" to asOf.toString(),
            "window" to WINDOW,
            "topics" to summaries.filter { it.first == "TOPIC" }.map { it.second },
            "sectors" to summaries.filter { it.first == "SECTOR" }.map { it.second }
        )
    }

    /**
     * One entry per ticker in [tickerByAssetId] that svc-data's `/news/sentiment` payload has
     * points for. Empty when none has.
     */
    fun assets(
        raw: Map<String, Any>,
        tickerByAssetId: Map<String, String>
    ): Map<String, Any> {
        val byAsset = (raw["data"] as? Map<*, *>).orEmpty()
        val scored =
            tickerByAssetId
                .mapValues { (assetId, _) ->
                    scores(byAsset[assetId])
                }.filterValues { it.isNotEmpty() }
        val asOf = scored.values.flatten().maxOfOrNull { it.date } ?: return emptyMap()
        return mapOf(
            "asOf" to asOf.toString(),
            "window" to WINDOW,
            "assets" to
                scored.map { (assetId, points) ->
                    val latest = points.filter { it.date > asOf.minusDays(WINDOW_DAYS) }
                    val prior = points.filter { it.date <= asOf.minusDays(WINDOW_DAYS) }
                    compare(scoreTotals(latest), scoreTotals(prior)) +
                        ("ticker" to tickerByAssetId.getValue(assetId))
                }
        )
    }

    /** `latest` and `prior` windows plus a worded `direction` between them, when both have articles. */
    private fun compare(
        latest: Window?,
        prior: Window?
    ): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        latest?.let { result["latest"] = it.fields }
        prior?.let { result["prior"] = it.fields }
        if (latest != null && prior != null) {
            result["direction"] = direction(latest.measure - prior.measure)
        }
        return result
    }

    private fun direction(change: BigDecimal): String =
        when {
            change >= DIRECTION_BAND -> "improving"
            change <= DIRECTION_BAND.negate() -> "souring"
            else -> "steady"
        }

    /**
     * Article counts with the positive and negative share as whole percentages, measured by the
     * net balance `(positive − negative) / total`; null for a window with no articles.
     */
    private fun countTotals(points: List<CountPoint>): Window? {
        val positive = points.sumOf { it.positive }
        val neutral = points.sumOf { it.neutral }
        val negative = points.sumOf { it.negative }
        val total = positive + neutral + negative
        return if (total == 0) {
            null
        } else {
            Window(
                mapOf(
                    "articles" to total,
                    "positive" to positive,
                    "neutral" to neutral,
                    "negative" to negative,
                    "positivePct" to percent(positive, total),
                    "negativePct" to percent(negative, total)
                ),
                ratio(BigDecimal(positive - negative), total)
            )
        }
    }

    /**
     * Article count and a worded tone, measured by the article-weighted mean score; null for a
     * window with no articles.
     */
    private fun scoreTotals(points: List<ScorePoint>): Window? {
        val articles = points.sumOf { it.count }
        return if (articles == 0) {
            null
        } else {
            val score =
                ratio(
                    points.fold(BigDecimal.ZERO) { sum, point -> sum + point.score * BigDecimal(point.count) },
                    articles
                )
            Window(mapOf("articles" to articles, "tone" to tone(score)), score)
        }
    }

    private fun tone(score: BigDecimal): String =
        when {
            score >= SentimentBands.POSITIVE -> "positive"
            score <= SentimentBands.NEGATIVE -> "negative"
            else -> "mixed"
        }

    private fun percent(
        part: Int,
        total: Int
    ): Int = ratio(BigDecimal(part * PERCENT), total).setScale(0, RoundingMode.HALF_UP).toInt()

    private fun ratio(
        numerator: BigDecimal,
        denominator: Int
    ): BigDecimal = numerator.divide(BigDecimal(denominator), SCALE, RoundingMode.HALF_UP)

    private fun points(raw: Any?): List<CountPoint> =
        (raw as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map {
            CountPoint(
                LocalDate.parse(it["date"].toString()),
                int(it["positive"]),
                int(it["neutral"]),
                int(it["negative"])
            )
        }

    private fun scores(raw: Any?): List<ScorePoint> =
        (raw as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map {
            ScorePoint(
                LocalDate.parse(it["date"].toString()),
                int(it["count"]),
                BigDecimal(it["normalized"].toString())
            )
        }

    private fun int(value: Any?): Int = (value as? Number)?.toInt() ?: 0

    private data class CountPoint(
        val date: LocalDate,
        val positive: Int,
        val neutral: Int,
        val negative: Int
    )

    private data class ScorePoint(
        val date: LocalDate,
        val count: Int,
        val score: BigDecimal
    )

    /** What the model reads ([fields]) and the internal figure the direction is judged on ([measure]). */
    private data class Window(
        val fields: Map<String, Any>,
        val measure: BigDecimal
    )

    private const val SCALE = 2
    private const val PERCENT = 100

    // A move in the balance or score smaller than this is noise, not a change of direction.
    private val DIRECTION_BAND = BigDecimal("0.10")
    private const val WINDOW = "latest = the 7 days ending asOf; prior = the 7 days before that"
}