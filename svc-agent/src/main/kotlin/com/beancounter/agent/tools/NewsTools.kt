package com.beancounter.agent.tools

import com.beancounter.agent.clients.AlphaVantageNewsClient
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service

/**
 * Tools for fetching financial news and market sentiment.
 */
@Service
class NewsTools(
    private val newsClient: AlphaVantageNewsClient
) {
    private val log = LoggerFactory.getLogger(NewsTools::class.java)

    @Tool(description = NEWS_DESC)
    fun getNews(
        @ToolParam(description = TICKERS_DESC) tickers: String,
        @ToolParam(description = MARKET_DESC, required = false) market: String? = null,
        @ToolParam(description = TOPICS_DESC, required = false) topics: String? = null
    ): Map<String, Any> {
        log.debug("getNews called: tickers={}, market={}, topics={}", tickers, market, topics)
        val raw = newsClient.getNewsSentiment(tickers, market, topics)
        val feed = raw["feed"] as? List<*>
        return if (raw.isEmpty() || feed.isNullOrEmpty()) {
            log.debug("getNews: no_coverage for tickers={}, market={}", tickers, market)
            mapOf(
                "status" to "no_coverage",
                "tickers" to tickers,
                "message" to NO_COVERAGE_MESSAGE
            )
        } else {
            log.debug("getNews: {} articles returned for tickers={}", feed.size, tickers)
            raw
        }
    }

    @Tool(description = MARKET_NEWS_DESC)
    fun getMarketNews(
        @ToolParam(description = SCOPE_DESC) scope: String
    ): Map<String, Any> {
        val key = scope.trim().lowercase()
        val symbols = if (key.isBlank() || key in MARKET_ALIASES) INDEX_PROXIES else SECTOR_ETFS[key]
        if (symbols == null) {
            log.debug("getMarketNews: unknown scope '{}'", scope)
            return mapOf(
                "status" to "unknown_scope",
                "scope" to scope,
                "message" to UNKNOWN_SCOPE_MESSAGE,
                "supportedScopes" to (listOf("market") + SECTOR_ETFS.keys.sorted())
            )
        }
        log.debug("getMarketNews: scope={} -> {}", scope, symbols)
        val raw = newsClient.getMarketNews(symbols)
        val feed = raw["feed"] as? List<*>
        return if (raw.isEmpty() || feed.isNullOrEmpty()) {
            mapOf(
                "status" to "no_coverage",
                "scope" to scope,
                "message" to NO_COVERAGE_MESSAGE
            )
        } else {
            raw
        }
    }

    companion object {
        const val NEWS_DESC =
            "Get recent news articles and sentiment analysis for given ticker symbols. " +
                "Returns headlines, summaries, sentiment scores (Bullish/Bearish/Neutral), " +
                "and relevance scores per ticker. " +
                "IMPORTANT: Live news coverage is primarily US-listed equities. " +
                "Non-US tickers (e.g. NZX, ASX, LSE) often return no coverage — the response " +
                "will be {status:'no_coverage', ...}. When that happens, do NOT retry with " +
                "the same or a re-formatted ticker; instead provide a concise " +
                "general-knowledge summary of the company/security from your training data, " +
                "clearly labelled as general knowledge rather than live news. " +
                "NEVER mention the name of the underlying data provider in your response. " +
                "Use this when users ask about news, market sentiment, or what's " +
                "happening with specific assets."
        const val TICKERS_DESC =
            "Comma-separated ticker symbols WITHOUT exchange prefix, e.g. 'AAPL' or " +
                "'AAPL,MSFT,VOO'. For non-US listings, pass just the local symbol " +
                "(e.g. 'GNE' for NZX:GNE) and set the market parameter."
        const val MARKET_DESC =
            "Exchange/market code (e.g. 'NZX', 'ASX', 'LON', 'SGX'). Required for " +
                "non-US tickers so the system resolves the correct exchange. " +
                "Omit for US markets (NASDAQ, NYSE, AMEX)."
        const val TOPICS_DESC =
            "Optional topic filter: earnings, ipo, mergers_and_acquisitions, " +
                "financial_markets, economy_fiscal, economy_monetary, " +
                "economy_macro, energy_transportation, finance, " +
                "life_sciences, manufacturing, real_estate, " +
                "retail_wholesale, technology"
        const val NO_COVERAGE_MESSAGE =
            "No live news coverage available for this ticker. This is common for " +
                "non-US listings. Provide a general-knowledge summary instead — do not retry."

        const val MARKET_NEWS_DESC =
            "Get market-wide or sector-wide news and sentiment via index/sector proxies — the " +
                "macro context that per-holding `getNews` misses (e.g. a jobs report, a Fed " +
                "decision, or a broad sell-off that moves a portfolio but isn't tagged to any one " +
                "ticker). Returns the same shape as getNews. Use this when the user asks why the " +
                "market or their portfolio moved, what's happening today, or about a sector's " +
                "conditions. To attribute a portfolio's move, also call this once per sector the " +
                "holdings belong to. NEVER mention the underlying data provider."
        const val SCOPE_DESC =
            "What to fetch news for: 'market' for broad macro/market-wide news, or a sector name. " +
                "Supported sectors: technology, financials, energy, healthcare, " +
                "consumer_discretionary, consumer_staples, industrials, materials, utilities, " +
                "real_estate, communication. Pass one scope per call."
        const val UNKNOWN_SCOPE_MESSAGE =
            "Unrecognised scope. Use 'market' for macro news or one of the listed sector names."

        /** Broad-market index proxies — surface macro headlines (jobs, Fed, market-wide moves). */
        private val INDEX_PROXIES = listOf("GSPC.INDX", "DJI.INDX")

        /**
         * Sector → SPDR sector ETF proxy. Sector ETFs carry strong EODHD news coverage, so their
         * headlines stand in for sector-wide sentiment. Keys are the scope vocabulary in [SCOPE_DESC].
         */
        private val SECTOR_ETFS =
            mapOf(
                "technology" to listOf("XLK.US"),
                "financials" to listOf("XLF.US"),
                "energy" to listOf("XLE.US"),
                "healthcare" to listOf("XLV.US"),
                "consumer_discretionary" to listOf("XLY.US"),
                "consumer_staples" to listOf("XLP.US"),
                "industrials" to listOf("XLI.US"),
                "materials" to listOf("XLB.US"),
                "utilities" to listOf("XLU.US"),
                "real_estate" to listOf("XLRE.US"),
                "communication" to listOf("XLC.US")
            )

        /** Scope strings that all mean "broad market". */
        private val MARKET_ALIASES = setOf("market", "markets", "macro", "broad", "overall", "index")
    }
}