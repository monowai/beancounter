package com.beancounter.marketdata.providers.eodhd

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Tunable knobs for EODHD news ranking. Same intent as `NewsProperties` for AlphaVantage —
 * shape the LLM tool_result payload so it stays small enough to fold back into the second
 * inference call without blowing input-token budgets.
 */
@ConfigurationProperties(prefix = "beancounter.market.providers.eodhd.news")
data class EodhdNewsProperties(
    /**
     * Overall cap on articles returned to the caller after ranking. A portfolio briefing asks for
     * every holding in one call, so this budget is shared across the requested symbols.
     */
    val maxArticles: Int = 12,
    /**
     * Per-symbol slice of [maxArticles]. Bounds how much of the shared budget any one well-covered
     * ticker can consume before the other holdings have been served.
     */
    val maxArticlesPerSymbol: Int = 3,
    /** How many articles to ask EODHD for per ticker before we rank + merge. */
    val providerLimit: Int = 50,
    /**
     * Re-hit EODHD when the per-ticker cache row in `news_fetch` is older than this many hours.
     * Default 6 → ~4 refreshes/day per active ticker, well within EODHD's ~1200 req/day quota.
     */
    val refreshAfterHours: Long = 6,
    /**
     * Articles whose `published` timestamp is older than this many days get pruned by
     * [com.beancounter.marketdata.providers.eodhd.news.NewsRetentionSchedule].
     */
    val retentionDays: Long = 30,
    /**
     * Recency window passed as `from` on topic-tag queries (`t=<tag>`). EODHD's topic results are
     * relevance-sorted rather than date-sorted, and without `from` return months-old items — this
     * bounds the window to something a macro briefing actually cares about.
     */
    val topicWindowDays: Long = 14
)