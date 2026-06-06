package com.beancounter.marketdata.providers

/**
 * Provider-agnostic news interface. Each implementation projects its vendor's response into the
 * shape svc-agent's `NewsTools` expects: `{ "feed": [<projected articles>], "count": N }` plus an
 * empty map when there's no coverage.
 *
 * Today two providers exist (AlphaVantage + EODHD). The facade in [NewsServiceFacade] picks one
 * at request time based on `beancounter.market.news.provider` (default: `alpha`).
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

    /**
     * Market / sector news by verbatim proxy symbols (e.g. an index `GSPC.INDX` or a sector ETF
     * `XLK.US`) rather than held tickers — the macro/sector signal that per-holding news misses.
     *
     * @param symbols fully-qualified provider symbols, already exchange-suffixed; NOT resolved
     * @param topics  optional topic filter; provider-defined vocabulary
     *
     * Default returns an empty map: only providers with index/ETF news coverage (EODHD) implement
     * it. AlphaVantage callers get the standard no-coverage signal.
     */
    fun getMarketNews(
        symbols: List<String>,
        topics: String? = null
    ): Map<String, Any> = emptyMap()
}