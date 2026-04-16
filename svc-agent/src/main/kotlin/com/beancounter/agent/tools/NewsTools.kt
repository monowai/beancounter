package com.beancounter.agent.tools

import com.beancounter.agent.clients.AlphaVantageNewsClient
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
    @Tool(description = NEWS_DESC)
    fun getNews(
        @ToolParam(description = TICKERS_DESC) tickers: String,
        @ToolParam(description = MARKET_DESC, required = false) market: String? = null,
        @ToolParam(description = TOPICS_DESC, required = false) topics: String? = null
    ): Map<String, Any> {
        val raw = newsClient.getNewsSentiment(tickers, market, topics)
        val feed = raw["feed"] as? List<*>
        return if (raw.isEmpty() || feed.isNullOrEmpty()) {
            mapOf(
                "status" to "no_coverage",
                "tickers" to tickers,
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
    }
}