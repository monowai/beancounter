package com.beancounter.agent.tools

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Compresses svc-data's daily sentiment rows into a this-week against last-week comparison, so a
 * review reads two small totals per series instead of a month of daily points. Pure; no I/O.
 *
 * Both windows end on the newest day present in the data, not on the wall clock — the result
 * carries that day as `asOf`, so stale data shows as stale rather than as a quiet week.
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
                        compare(countTotals(latest), countTotals(prior), "net") + ("name" to one["name"].toString())
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
                    compare(scoreTotals(latest), scoreTotals(prior), "score") +
                        ("ticker" to tickerByAssetId.getValue(assetId))
                }
        )
    }

    /** `latest` and `prior` totals plus the change in [measure] between them, when both have one. */
    private fun compare(
        latest: Map<String, Any>?,
        prior: Map<String, Any>?,
        measure: String
    ): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        latest?.let { result["latest"] = it }
        prior?.let { result["prior"] = it }
        val now = latest?.get(measure) as? BigDecimal
        val before = prior?.get(measure) as? BigDecimal
        if (now != null && before != null) {
            result["${measure}Change"] = now - before
        }
        return result
    }

    /** Article counts and net balance `(positive − negative) / total`; null for a window with no articles. */
    private fun countTotals(points: List<CountPoint>): Map<String, Any>? {
        val positive = points.sumOf { it.positive }
        val neutral = points.sumOf { it.neutral }
        val negative = points.sumOf { it.negative }
        val total = positive + neutral + negative
        return if (total == 0) {
            null
        } else {
            mapOf(
                "positive" to positive,
                "neutral" to neutral,
                "negative" to negative,
                "net" to ratio(BigDecimal(positive - negative), total)
            )
        }
    }

    /** Article count and the article-weighted mean score; null for a window with no articles. */
    private fun scoreTotals(points: List<ScorePoint>): Map<String, Any>? {
        val articles = points.sumOf { it.count }
        return if (articles == 0) {
            null
        } else {
            mapOf(
                "articles" to articles,
                "score" to
                    ratio(
                        points.fold(BigDecimal.ZERO) { sum, point -> sum + point.score * BigDecimal(point.count) },
                        articles
                    )
            )
        }
    }

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

    private const val SCALE = 2
    private const val WINDOW = "latest = the 7 days ending asOf; prior = the 7 days before that"
}