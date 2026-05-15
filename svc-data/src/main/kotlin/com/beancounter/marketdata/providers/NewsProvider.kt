package com.beancounter.marketdata.providers

/**
 * Provider-agnostic news interface. Each implementation projects its vendor's response into the
 * shape svc-agent's `NewsTools` expects: `{ "feed": [<projected articles>], "count": N }` plus an
 * empty map when there's no coverage.
 *
 * Today two providers exist (AlphaVantage + EODHD). The facade in [NewsServiceFacade] picks one
 * at request time based on `beancounter.market.providers.news` (default: `alpha`).
 */
interface NewsProvider {
    /**
     * @param tickers comma-separated symbols (no exchange suffix; the [market] argument carries that)
     * @param market  optional exchange/market code. Used by providers that need it to disambiguate.
     * @param topics  optional topic filter; provider-defined vocabulary
     */
    fun getNewsSentiment(
        tickers: String,
        market: String? = null,
        topics: String? = null
    ): Map<String, Any>
}