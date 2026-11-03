package com.beancounter.marketdata.news.eodhd

import com.beancounter.marketdata.news.EmbeddingCodec
import com.beancounter.marketdata.news.NewsArticle
import com.beancounter.marketdata.news.NewsEmbeddingProperties
import com.beancounter.marketdata.news.VectorMath
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * The news briefing an agent reads: stored articles ranked, de-duplicated and capped per
 * symbol, then projected to the AV-compatible feed entry. Pure; no I/O.
 */
internal class NewsBriefing(
    private val newsProperties: EodhdNewsProperties,
    private val embeddingProperties: NewsEmbeddingProperties
) {
    /**
     * Rank the stored window down to the articles worth spending prompt tokens on.
     *
     * Recency leads. EODHD polarity saturates at ±1.0 across a large share of the feed, so ranking
     * on magnitude alone was an arbitrary tie-break that floated weeks-old articles above the ones
     * explaining today's move — a holding could drop 8% and the agent would see a month-old
     * industry round-up instead. Polarity now only separates articles published at the same instant.
     *
     * Selection is round-robin across the requested symbols: each takes its newest article before
     * any takes a second. A portfolio briefing requests every holding in one call, and a flat
     * top-N let one well-covered mega-cap consume the whole budget — leaving the LLM with nothing
     * on the holding that actually moved, which it then filled in from training data.
     */
    fun rank(
        stored: List<NewsArticle>,
        symbols: List<String>,
        topics: String?
    ): List<NewsArticle> {
        val eligible = stored.filter { topics.isNullOrBlank() || matchesTopic(it, topics) }
        if (eligible.isEmpty()) return emptyList()

        // Dedup inside each symbol's slice, never across the union. Template spam is near-identical
        // across tickers ("X fell more than the market"), so clustering globally would let one
        // holding's copy suppress another's — and a symbol whose only article is the suppressed one
        // drops out of the briefing entirely. Per-symbol clustering still kills the flood within a
        // symbol, which is what actually consumed the budget.
        val perSymbol =
            symbols.map { symbol ->
                suppressNearDuplicates(
                    eligible.filter { article -> article.tickerLinks.any { it.ticker == symbol } }
                ).take(newsProperties.maxArticlesPerSymbol)
            }

        // Keyed by id so an article tagged to several requested holdings is only counted once.
        val picked = LinkedHashMap<String, NewsArticle>()
        rounds@ for (round in 0 until newsProperties.maxArticlesPerSymbol) {
            for (candidates in perSymbol) {
                if (picked.size >= newsProperties.maxArticles) break@rounds
                candidates.getOrNull(round)?.let { picked.putIfAbsent(it.id, it) }
            }
        }
        return picked.values.sortedWith(NEWEST_FIRST).take(newsProperties.maxArticles)
    }

    /**
     * Greedy near-duplicate suppression so template-spam articles ("X fell more than the market"
     * ×N from different wire services) can't flood one symbol's slice. Runs purely off
     * already-persisted [NewsArticle.embedding] vectors — independent of whether the embedder is
     * currently active, so a vector computed while the feature was on still dedups correctly after
     * it's switched back off. Zero-cost, and a behavioral no-op, when nothing in [candidates]
     * carries an embedding yet (today's default, and every window before this feature shipped).
     *
     * Returns newest-first, so the representative kept for a duplicate group is always the newest
     * article and the caller's per-symbol `take` is a recency cut. Articles without a stored
     * embedding are always kept — there's nothing to compare them against, so silently dropping one
     * on a false-suspicion basis would be strictly worse than the pre-dedup behavior.
     *
     * Only vectors from the same [NewsArticle.embeddingModel] are compared. A model change
     * invalidates stored vectors but doesn't clear them — old and new rows coexist until each is
     * re-embedded — and comparing across models is meaningless even at equal width (and rejected
     * outright by [VectorMath.dot] when the widths differ).
     *
     * O(n^2) against the kept-representative set — fine at a symbol's slice of the retention
     * window (tens of articles).
     */
    private fun suppressNearDuplicates(candidates: List<NewsArticle>): List<NewsArticle> {
        val ordered = candidates.sortedWith(NEWEST_FIRST)
        if (ordered.none { it.embedding != null }) return ordered

        val kept = mutableListOf<Representative>()
        val result = mutableListOf<NewsArticle>()
        for (article in ordered) {
            val vector = article.embedding?.let { EmbeddingCodec.decode(it) }
            if (vector == null) {
                result.add(article)
                continue
            }
            val isDuplicate =
                kept.any { representative ->
                    representative.model == article.embeddingModel &&
                        representative.vector.size == vector.size &&
                        VectorMath.dot(vector, representative.vector) >=
                        embeddingProperties.similarityThreshold
                }
            if (!isDuplicate) {
                kept.add(Representative(article.embeddingModel, vector))
                result.add(article)
            }
        }
        return result
    }

    /** A kept cluster head: its vector plus the model that produced it (never compared across models). */
    private class Representative(
        val model: String?,
        val vector: FloatArray
    )

    private fun matchesTopic(
        article: NewsArticle,
        topic: String
    ): Boolean {
        val needle = topic.trim().uppercase()
        // EODHD's own tags first (exact vendor match), then BC's anchor-derived topics — closes
        // the coverage gap when EODHD ships no tags for an article at all.
        return article.tags.any { it.uppercase() == needle } ||
            article.derivedTopics.any { it.uppercase() == needle }
    }

    fun project(article: NewsArticle): Map<String, Any> {
        val polarity = article.polarity.toDouble()
        return mapOf(
            "title" to article.title,
            "summary" to (article.summary?.takeIf { it.isNotBlank() } ?: article.content.take(SUMMARY_CHARS)),
            "source" to article.link,
            "timePublished" to article.published.toString(),
            "sentimentLabel" to labelFor(polarity),
            "sentimentScore" to scaledScore(polarity),
            "relevance" to 1.0,
            "tickerSentimentLabel" to labelFor(polarity),
            "tickerSentimentScore" to scaledScore(polarity),
            "polarity" to polarity,
            "symbols" to article.tickerLinks.map { it.ticker },
            "tags" to article.tags.toList()
        )
    }

    private fun scaledScore(polarity: Double): Double =
        BigDecimal(polarity).setScale(SCORE_SCALE, RoundingMode.HALF_UP).toDouble()

    private fun labelFor(polarity: Double): String =
        when {
            polarity >= BULLISH_THRESHOLD -> "Bullish"
            polarity <= BEARISH_THRESHOLD -> "Bearish"
            else -> "Neutral"
        }

    private companion object {
        // Recency first; polarity magnitude only separates articles sharing a publish instant.
        // externalId is the final tie-break so a given window always ranks the same way.
        val NEWEST_FIRST =
            compareByDescending<NewsArticle> { it.published }
                .thenByDescending { abs(it.polarity.toDouble()) }
                .thenBy { it.externalId }

        const val SUMMARY_CHARS = 400
        const val SCORE_SCALE = 4
        const val BULLISH_THRESHOLD = 0.35
        const val BEARISH_THRESHOLD = -0.35
    }
}