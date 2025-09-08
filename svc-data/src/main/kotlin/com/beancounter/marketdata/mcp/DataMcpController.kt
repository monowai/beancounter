package com.beancounter.marketdata.mcp

import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Portfolio
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * REST Controller exposing Data MCP Server functionality for AI agents.
 *
 * This controller provides HTTP endpoints that expose market data, asset management,
 * portfolio, and transaction functionality for AI agent integration.
 */
@RestController
@RequestMapping("/mcp")
@Tag(name = "Data MCP", description = "Model Context Protocol endpoints for market data and asset management")
class DataMcpController(
    private val dataMcpServer: DataMcpServer
) {
    @GetMapping("/ping")
    @Operation(
        summary = "Ping endpoint",
        description = "Simple unauthenticated ping endpoint for health checks and debugging"
    )
    fun ping(): Map<String, String> =
        mapOf(
            "status" to "ok",
            "service" to "data-mcp",
            "timestamp" to
                java.time.Instant
                    .now()
                    .toString()
        )

    @GetMapping("/tools")
    @Operation(
        summary = "Get available MCP tools",
        description = "Returns a list of all available MCP tools and their descriptions"
    )
    fun getAvailableTools(): Map<String, String> = dataMcpServer.getAvailableTools()

    @PostMapping("/asset/find-or-create")
    @Operation(
        summary = "Find or create asset",
        description = "Find an existing asset or create a new one by market and code"
    )
    fun findOrCreateAsset(
        @Parameter(description = "Market code (e.g., NYSE, NASDAQ)")
        @RequestParam market: String,
        @Parameter(description = "Asset code/ticker (e.g., AAPL, MSFT)")
        @RequestParam code: String,
        @Parameter(description = "Asset category (default: EQUITY)")
        @RequestParam(defaultValue = "EQUITY") category: String
    ): Asset = dataMcpServer.findOrCreateAsset(market, code, category)

    @GetMapping("/asset/{assetId}")
    @Operation(summary = "Get asset by ID", description = "Get asset information by asset ID")
    fun getAsset(
        @Parameter(description = "Asset identifier")
        @PathVariable assetId: String
    ): Asset = dataMcpServer.getAsset(assetId)

    @GetMapping("/portfolios")
    @Operation(summary = "Get all portfolios", description = "Get all portfolios for the current user")
    fun getPortfolios(): PortfoliosResponse = dataMcpServer.getPortfolios()

    @GetMapping("/portfolio/{portfolioId}")
    @Operation(summary = "Get portfolio by ID", description = "Get a specific portfolio by ID")
    fun getPortfolio(
        @Parameter(description = "Portfolio identifier")
        @PathVariable portfolioId: String
    ): Portfolio = dataMcpServer.getPortfolio(portfolioId)

    @GetMapping("/portfolio/code/{code}")
    @Operation(summary = "Get portfolio by code", description = "Get portfolio by code")
    fun getPortfolioByCode(
        @Parameter(description = "Portfolio code")
        @PathVariable code: String
    ): Portfolio = dataMcpServer.getPortfolioByCode(code)

    @GetMapping("/portfolios/holding/{assetId}")
    @Operation(summary = "Get portfolios holding asset", description = "Get portfolios where a specific asset is held")
    fun getPortfoliosWhereHeld(
        @Parameter(description = "Asset identifier")
        @PathVariable assetId: String,
        @Parameter(description = "Trade date in YYYY-MM-DD format (optional)")
        @RequestParam(required = false) tradeDate: String?
    ): PortfoliosResponse = dataMcpServer.getPortfoliosWhereHeld(assetId, tradeDate)

    @GetMapping("/portfolio/{portfolioId}/transactions")
    @Operation(summary = "Get portfolio transactions", description = "Get transactions for a portfolio")
    fun getTransactionsForPortfolio(
        @Parameter(description = "Portfolio identifier")
        @PathVariable portfolioId: String,
        @Parameter(description = "Trade date in YYYY-MM-DD format (optional)")
        @RequestParam(required = false) tradeDate: String?
    ): Collection<com.beancounter.common.model.Trn> = dataMcpServer.getTransactionsForPortfolio(portfolioId, tradeDate)

    @GetMapping("/asset/{assetId}/market-data")
    @Operation(summary = "Get market data", description = "Get market data for an asset on a specific date")
    fun getMarketData(
        @Parameter(description = "Asset identifier")
        @PathVariable assetId: String,
        @Parameter(description = "Date in YYYY-MM-DD format")
        @RequestParam date: String
    ): Map<String, Any> = dataMcpServer.getMarketData(assetId, date)

    @GetMapping("/fx-rates")
    @Operation(summary = "Get FX rates", description = "Get FX rates between currencies")
    fun getFxRates(
        @Parameter(description = "From currency code (e.g., USD)")
        @RequestParam fromCurrency: String,
        @Parameter(description = "To currency code (e.g., EUR)")
        @RequestParam toCurrency: String,
        @Parameter(description = "Rate date in YYYY-MM-DD format (optional)")
        @RequestParam(required = false) rateDate: String?
    ): FxResponse = dataMcpServer.getFxRates(fromCurrency, toCurrency, rateDate)

    @GetMapping("/markets")
    @Operation(summary = "Get all markets", description = "Get all available markets")
    fun getMarkets(): MarketResponse = dataMcpServer.getMarkets()

    @GetMapping("/currencies")
    @Operation(summary = "Get all currencies", description = "Get all available currencies")
    fun getCurrencies(): Collection<com.beancounter.common.model.Currency> = dataMcpServer.getCurrencies()

    @GetMapping("/prices/current")
    @Operation(summary = "Get current prices", description = "Get current market data for multiple assets")
    fun getCurrentPrices(
        @Parameter(description = "Comma-separated list of asset identifiers")
        @RequestParam assetIds: String,
        @Parameter(description = "Date in YYYY-MM-DD format (optional, defaults to today)")
        @RequestParam(required = false) date: String?
    ): Map<String, Any> = dataMcpServer.getCurrentPrices(assetIds, date)
}