package com.beancounter.marketdata.providers.eodhd.model

import java.math.BigDecimal

/**
 * Single news article returned by EODHD's `/api/news` endpoint.
 *
 * EODHD ships richer per-article sentiment than AlphaVantage's NEWS_SENTIMENT — the [sentiment]
 * block carries polarity plus a pos/neg/neu probability split, alongside the multi-ticker
 * [symbols] tagging that lets the LLM see which other names an article connects to.
 */
data class EodhdNewsArticle(
    val date: String = "",
    val title: String = "",
    val content: String = "",
    val link: String = "",
    val symbols: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val sentiment: EodhdArticleSentiment? = null
)

/**
 * Per-article sentiment block. `polarity` is the overall score (roughly -1..1);
 * pos/neg/neu sum to ~1.0.
 */
data class EodhdArticleSentiment(
    val polarity: BigDecimal = BigDecimal.ZERO,
    val neg: BigDecimal = BigDecimal.ZERO,
    val neu: BigDecimal = BigDecimal.ZERO,
    val pos: BigDecimal = BigDecimal.ZERO
)