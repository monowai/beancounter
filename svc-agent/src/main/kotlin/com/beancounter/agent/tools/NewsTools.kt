package com.beancounter.agent.tools

import com.beancounter.agent.clients.AlphaVantageNewsClient
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service

/**
 * Tools for fetching financial news and market sentiment via Alpha Vantage.
 */
@Service
class NewsTools(
    private val newsClient: AlphaVantageNewsClient
) {
    @Tool(description = NEWS_DESC)
    fun getNews(
        @ToolParam(description = TICKERS_DESC) tickers: String,
        @ToolParam(description = TOPICS_DESC, required = false) topics: String? = null
    ): Map<String, Any> = newsClient.getNewsSentiment(tickers, topics)

    companion object {
        const val NEWS_DESC =
            "Get recent news articles and sentiment analysis for given ticker symbols. " +
                "Returns headlines, summaries, sentiment scores (Bullish/Bearish/Neutral), " +
                "and relevance scores per ticker. Use this when users ask about news, " +
                "market sentiment, or what's happening with specific assets."
        const val TICKERS_DESC =
            "Comma-separated ticker symbols, e.g. 'AAPL' or 'AAPL,MSFT,VOO'"
        const val TOPICS_DESC =
            "Optional topic filter: earnings, ipo, mergers_and_acquisitions, " +
                "financial_markets, economy_fiscal, economy_monetary, " +
                "economy_macro, energy_transportation, finance, " +
                "life_sciences, manufacturing, real_estate, " +
                "retail_wholesale, technology"
    }
}