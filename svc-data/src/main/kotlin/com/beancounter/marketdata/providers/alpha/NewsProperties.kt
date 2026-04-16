package com.beancounter.marketdata.providers.alpha

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Tunable knobs for news & sentiment fetching. The LLM is cost-sensitive to
 * the tool_result payload — each returned article shows up in the second
 * inference call's input tokens. These settings shape that payload:
 *
 * - [providerLimit] is how many raw articles we ask Alpha Vantage for;
 *   a larger pool means stronger ranking, at zero extra LLM cost.
 * - [maxArticles] is how many we pass to the LLM after ranking.
 * - [relevanceThreshold] drops low-signal mentions (articles that merely
 *   name the ticker in passing).
 * - [businessDaysBack] is the recency window; older news is ignored.
 */
@ConfigurationProperties(prefix = "beancounter.market.providers.alpha.news")
data class NewsProperties(
    /** Minimum per-ticker `relevance_score` to keep an article. 0.0–1.0. */
    val relevanceThreshold: Double = 0.3,
    /** Top-N articles returned to the caller after ranking. */
    val maxArticles: Int = 5,
    /** Recency window in business days (skips weekends). */
    val businessDaysBack: Int = 5,
    /** How many articles to ask Alpha Vantage for before we rank. */
    val providerLimit: Int = 50
)