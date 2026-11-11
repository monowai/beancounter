package com.beancounter.agent.tools

import com.beancounter.agent.client.AssetClient
import com.beancounter.agent.clients.NewsClient
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service

/**
 * Tools for fetching financial news and market sentiment.
 */
@Service
class NewsTools(
    private val newsClient: NewsClient,
    private val assetClient: AssetClient
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
                STATUS to NO_COVERAGE,
                "tickers" to tickers,
                MESSAGE to NO_COVERAGE_MESSAGE
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
        val isMarketScope = key.isBlank() || key in MARKET_ALIASES
        val raw =
            when {
                isMarketScope -> {
                    log.debug("getMarketNews: scope={} -> macro topics {}", scope, MACRO_TOPICS)
                    newsClient.getTopicNews(MACRO_TOPICS)
                }
                key in SECTOR_ETFS -> {
                    val symbols = SECTOR_ETFS.getValue(key)
                    log.debug("getMarketNews: scope={} -> {}", scope, symbols)
                    newsClient.getMarketNews(symbols)
                }
                else -> {
                    null
                }
            }
        if (raw == null) {
            log.debug("getMarketNews: unknown scope '{}'", scope)
            return mapOf(
                STATUS to "unknown_scope",
                "scope" to scope,
                MESSAGE to UNKNOWN_SCOPE_MESSAGE,
                "supportedScopes" to (listOf("market") + SECTOR_ETFS.keys.sorted())
            )
        }
        val feed = raw["feed"] as? List<*>
        return if (raw.isEmpty() || feed.isNullOrEmpty()) {
            mapOf(
                STATUS to NO_COVERAGE,
                "scope" to scope,
                // Macro topic coverage (market scope) and sector-ETF coverage (sector scope) fail
                // for different reasons and want different guidance — a sector ETF is still a
                // ticker-shaped symbol (NO_COVERAGE_MESSAGE's "this ticker" framing fits), while
                // the market scope has no ticker at all.
                MESSAGE to if (isMarketScope) TOPIC_NO_COVERAGE_MESSAGE else NO_COVERAGE_MESSAGE
            )
        } else {
            raw
        }
    }

    @Tool(description = MARKET_SENTIMENT_DESC)
    fun getMarketSentimentTrend(): Map<String, Any> {
        val trend = SentimentTrend.market(newsClient.getMarketSentiment(SentimentTrend.FETCH_DAYS))
        return trend.ifEmpty { mapOf(STATUS to NO_COVERAGE, MESSAGE to SENTIMENT_NO_COVERAGE_MESSAGE) }
    }

    @Tool(description = ASSET_SENTIMENT_DESC)
    fun getAssetSentimentTrend(
        @ToolParam(description = SENTIMENT_TICKERS_DESC) tickers: String,
        @ToolParam(description = MARKET_DESC, required = false) market: String? = null
    ): Map<String, Any> {
        val resolvedMarket = market?.takeIf { it.isNotBlank() } ?: DEFAULT_MARKET
        val tickerByAssetId =
            tickers
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(MAX_SENTIMENT_TICKERS)
                .mapNotNull { ticker -> assetId(resolvedMarket, ticker)?.let { it to ticker } }
                .toMap()
        val trend =
            if (tickerByAssetId.isEmpty()) {
                emptyMap()
            } else {
                val raw = newsClient.getAssetSentiment(tickerByAssetId.keys.toList(), SentimentTrend.FETCH_DAYS)
                SentimentTrend.assets(raw, tickerByAssetId)
            }
        return trend.ifEmpty {
            mapOf(STATUS to NO_COVERAGE, "tickers" to tickers, MESSAGE to SENTIMENT_NO_COVERAGE_MESSAGE)
        }
    }

    /** The asset id for [ticker], or null when it does not resolve — one bad ticker never fails the rest. */
    private fun assetId(
        market: String,
        ticker: String
    ): String? =
        try {
            assetClient.getAsset(market, ticker).id
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.debug("getAssetSentimentTrend: {}:{} did not resolve: {}", market, ticker, e.message)
            null
        }

    companion object {
        const val MARKET_SENTIMENT_DESC =
            "Get the direction of news sentiment for each macro topic (stock markets, economy, " +
                "inflation, bonds, energy, commodities) and each sector, as separate series. Per " +
                "series, for the latest 7 days and the 7 days before: `articles`, the Positive / " +
                "Neutral / Negative counts, `positivePct` and `negativePct` (whole percentages of " +
                "the articles), and a `direction` of improving, souring or steady. Use it to say " +
                "whether the news flow around a topic or sector is improving or souring, and to " +
                "set a holding's own news against its sector. Say it in plain words with at most " +
                "one figure, e.g. 'six in ten inflation articles were negative this week, up " +
                "from four in ten'. Never add series together or quote one overall market " +
                "figure — say which topic or sector. A window with few articles is weak " +
                "evidence: say so. `prior` or `direction` is absent when a window has no " +
                "articles. `asOf` is the newest day counted. This counts headlines; " +
                "getMarketNews returns the headlines themselves. NEVER mention the underlying " +
                "data provider."
        const val ASSET_SENTIMENT_DESC =
            "Get the direction of news sentiment for specific tickers: per ticker, for the " +
                "latest 7 days and the 7 days before, the `articles` count and a `tone` of " +
                "positive, mixed or negative, plus a `direction` of improving, souring or " +
                "steady. Use it to say whether coverage of a holding is turning, and pair it " +
                "with getMarketSentimentTrend to tell a stock-specific shift from a sector-wide " +
                "one. Report the tone and direction in words; there is no score to quote. " +
                "Tickers with no coverage are absent — common for non-US listings; do not retry " +
                "them. A window with few articles is weak evidence: say so. NEVER mention the " +
                "underlying data provider."
        const val SENTIMENT_TICKERS_DESC =
            "Comma-separated ticker symbols WITHOUT exchange prefix, e.g. 'AAPL,MSFT'. At most " +
                "10 per call; all must be on the same market."
        const val SENTIMENT_NO_COVERAGE_MESSAGE =
            "No stored news sentiment for this request. Do not retry and do not estimate a " +
                "sentiment trend; leave it out of the answer."
        private const val MAX_SENTIMENT_TICKERS = 10
        private const val DEFAULT_MARKET = "US"
        private const val NO_COVERAGE = "no_coverage"
        private const val STATUS = "status"
        private const val MESSAGE = "message"

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
                "Omit for US listings: Beancounter's market for them is US, whatever the exchange."
        const val TOPICS_DESC =
            "Optional topic filter: earnings, ipo, mergers_and_acquisitions, " +
                "financial_markets, economy_fiscal, economy_monetary, " +
                "economy_macro, energy_transportation, finance, " +
                "life_sciences, manufacturing, real_estate, " +
                "retail_wholesale, technology"
        const val NO_COVERAGE_MESSAGE =
            "No live news coverage available for this ticker. This is common for " +
                "non-US listings. Provide a general-knowledge summary instead — do not retry. " +
                "Do not announce the missing coverage: start the answer with the heading and " +
                "carry the caveat as a short labelled line beneath it."

        const val MARKET_NEWS_DESC =
            "Get market-wide or sector-wide news and sentiment — the macro context that " +
                "per-holding `getNews` misses (e.g. a jobs report, a Fed decision, or a broad " +
                "sell-off that moves a portfolio but isn't tagged to any one ticker). 'market' " +
                "scope is topic-backed macro news (stock markets, economy, inflation, bonds, " +
                "energy, commodities); a sector scope is SPDR sector-ETF news. Returns the same " +
                "shape as getNews. Use this when the user asks why the market or their " +
                "portfolio moved, what's happening today, or about a sector's conditions. To " +
                "attribute a portfolio's move, also call this once per sector the holdings " +
                "belong to. NEVER mention the underlying data provider."
        const val SCOPE_DESC =
            "What to fetch news for: 'market' for broad topic-backed macro news, or a sector " +
                "name. Supported sectors: technology, financials, energy, healthcare, " +
                "consumer_discretionary, consumer_staples, industrials, materials, utilities, " +
                "real_estate, communication. Pass one scope per call."
        const val UNKNOWN_SCOPE_MESSAGE =
            "Unrecognised scope. Use 'market' for macro news or one of the listed sector names."
        const val TOPIC_NO_COVERAGE_MESSAGE =
            "No live macro topic coverage available right now. Summarise from general " +
                "knowledge, clearly labelled, without inventing headlines. Do not announce the " +
                "missing coverage: start the answer with the heading and carry the caveat as a " +
                "short labelled line beneath it."

        // MACRO_TOPICS surfaces broad-market macro headlines via provider topic tags rather than
        // single-stock index/ETF proxies (those produced noisy single-stock "X fell more than
        // market" articles). Must match MacroRefreshSchedule.MACRO_TOPICS in svc-data — both are
        // plain-string topic vocabulary, not a shared constant, since svc-agent has no compile-time
        // dependency on svc-data. SECTOR_ETFS stand in for sector-wide sentiment via SPDR sector
        // ETFs, which carry strong EODHD news coverage. SECTOR_ETFS keys are the scope vocabulary
        // in SCOPE_DESC. MARKET_ALIASES are the scope strings that all mean "broad market".
        private val MACRO_TOPICS =
            listOf(
                "stock markets",
                "economy",
                "inflation",
                "bonds",
                "energy",
                "commodities"
            )

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

        private val MARKET_ALIASES = setOf("market", "markets", "macro", "broad", "overall", "index")
    }
}