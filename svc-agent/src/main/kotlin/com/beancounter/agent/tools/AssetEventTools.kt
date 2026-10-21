package com.beancounter.agent.tools

import com.beancounter.agent.client.AssetClient
import com.beancounter.agent.client.EventClient
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service

/**
 * Corporate events (dividends, splits) of one asset. Reads no user data, so
 * an Asset Review can use these without seeing the caller's portfolios; the
 * portfolio-scoped loaders live in [EventTools].
 */
@Service
class AssetEventTools(
    private val eventClient: EventClient,
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
    }
}