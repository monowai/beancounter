package com.beancounter.agent.tools

import com.beancounter.agent.client.AssetClient
import com.beancounter.agent.client.EventClient
import com.beancounter.client.services.PortfolioServiceClient
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service

/**
 * Tools for corporate event lookup, loading and backfill.
 *
 * Portfolio-scoped tools come in pairs: a by-**code** variant for the
 * standard case (the user owns the portfolio and refers to it by its
 * short code), and a by-**id** variant
 * ([loadPortfolioEventsByPortfolioId], [backfillPortfolioEventsByPortfolioId])
 * for managed/shared portfolios where code is ambiguous. The by-code
 * variants resolve the code to an internal id inside the tool; the by-id
 * variants pass the caller-supplied id straight through. The frontend
 * exposes `portfolioId` in the page context when the by-id variants
 * should be picked.
 */
@Service
class EventTools(
    private val eventClient: EventClient,
    private val portfolioServiceClient: PortfolioServiceClient,
    private val assetClient: AssetClient
) {
    @Tool(description = ASSET_EVENTS_DESC)
    fun getAssetEvents(
        @ToolParam(
            description =
                "Internal Beancounter asset id (UUID-like). " +
                    "If the user gives a public ticker, use getAssetEventsByTicker instead."
        ) assetId: String
    ): Map<String, Any> = eventClient.getAssetEvents(assetId)

    @Tool(description = ASSET_EVENTS_BY_TICKER_DESC)
    fun getAssetEventsByTicker(
        @ToolParam(
            description = "Ticker symbol as quoted on the market, e.g. 'GOOG', 'AAPL'."
        ) ticker: String,
        @ToolParam(
            required = false,
            description =
                "Optional Beancounter market hint. Default 'US' covers every " +
                    "US-listed equity (NASDAQ/NYSE/AMEX/ARCA all collapse to " +
                    "the canonical US market server-side). Pass 'ASX', 'NZX', " +
                    "'SGX' etc. for non-US listings, or call listMarkets for " +
                    "the full set. Typos like 'NASAQ' and index labels like " +
                    "'DOW' / 'DOW JONES' are also accepted and resolved to US."
        ) market: String = "US"
    ): Map<String, Any> {
        val resolved = market.ifBlank { "US" }
        val asset = assetClient.getAsset(resolved, ticker)
        return eventClient.getAssetEvents(asset.id)
    }

    @Tool(description = LOAD_DESC)
    fun loadPortfolioEvents(
        @ToolParam(description = "Portfolio code as the user types it, e.g. 'TYLER'") portfolioCode: String,
        @ToolParam(description = "Date YYYY-MM-DD or 'today'") asAt: String = "today"
    ): Map<String, Any> {
        val portfolio = portfolioServiceClient.getPortfolioByCode(portfolioCode)
        return eventClient.loadPortfolioEvents(portfolio.id, asAt)
    }

    @Tool(description = LOAD_BY_ID_DESC)
    fun loadPortfolioEventsByPortfolioId(
        @ToolParam(
            description =
                "Internal portfolio id (UUID-like, ~22 chars). Use this when the page " +
                    "context provides `portfolioId` instead of `portfolioCode` " +
                    "(managed/shared portfolios)."
        ) portfolioId: String,
        @ToolParam(description = "Date YYYY-MM-DD or 'today'") asAt: String = "today"
    ): Map<String, Any> = eventClient.loadPortfolioEvents(portfolioId, asAt)

    @Tool(description = BACKFILL_DESC)
    fun backfillPortfolioEvents(
        @ToolParam(description = "Portfolio code as the user types it, e.g. 'TYLER'") portfolioCode: String,
        @ToolParam(description = "Start date YYYY-MM-DD") fromDate: String,
        @ToolParam(description = "End date YYYY-MM-DD; defaults to fromDate") toDate: String = ""
    ): Map<String, Any> {
        val portfolio = portfolioServiceClient.getPortfolioByCode(portfolioCode)
        return eventClient.backfillPortfolio(portfolio.id, fromDate, toDate.ifBlank { fromDate })
    }

    @Tool(description = BACKFILL_BY_ID_DESC)
    fun backfillPortfolioEventsByPortfolioId(
        @ToolParam(
            description =
                "Internal portfolio id (UUID-like, ~22 chars). Use this when the page " +
                    "context provides `portfolioId` instead of `portfolioCode` " +
                    "(managed/shared portfolios)."
        ) portfolioId: String,
        @ToolParam(description = "Start date YYYY-MM-DD") fromDate: String,
        @ToolParam(description = "End date YYYY-MM-DD; defaults to fromDate") toDate: String = ""
    ): Map<String, Any> = eventClient.backfillPortfolio(portfolioId, fromDate, toDate.ifBlank { fromDate })

    companion object {
        const val ASSET_EVENTS_DESC =
            "List all stored corporate events (dividends, splits) for an internal " +
                "Beancounter asset id, ordered by pay date, most recent first. " +
                "For public tickers, prefer getAssetEventsByTicker."
        const val ASSET_EVENTS_BY_TICKER_DESC =
            "List all stored corporate events (dividends, splits) for a publicly " +
                "listed ticker — e.g. 'how many dividends has GOOG paid?'. " +
                "Resolves the ticker on the named market into a Beancounter asset " +
                "(creating one on demand) and then returns the same payload as " +
                "getAssetEvents. Prefer this tool when the user names a ticker " +
                "rather than referring to a portfolio holding."
        const val LOAD_DESC =
            "Trigger a load of new corporate events from external providers for every asset " +
                "in a portfolio. Returns immediately; the load runs asynchronously."
        const val LOAD_BY_ID_DESC =
            "Same as loadPortfolioEvents but resolves the portfolio by its internal id. " +
                "Prefer this when the context exposes `portfolioId` (managed/shared portfolios)."
        const val BACKFILL_DESC =
            "Reprocess corporate events for a portfolio between two dates, generating any " +
                "missing dividend/split transactions. Use to reconcile or repair history."
        const val BACKFILL_BY_ID_DESC =
            "Same as backfillPortfolioEvents but resolves the portfolio by its internal id. " +
                "Prefer this when the context exposes `portfolioId` (managed/shared portfolios)."
    }
}