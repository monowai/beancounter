package com.beancounter.marketdata.providers.eodhd

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Tunable knobs for EODHD news ranking. Same intent as `NewsProperties` for AlphaVantage —
 * shape the LLM tool_result payload so it stays small enough to fold back into the second
 * inference call without blowing input-token budgets.
 */
@ConfigurationProperties(prefix = "beancounter.market.providers.eodhd.news")
data class EodhdNewsProperties(
    /** Top-N articles returned to the caller after ranking. */
    val maxArticles: Int = 5,
    /** How many articles to ask EODHD for per ticker before we rank + merge. */
    val providerLimit: Int = 50
)