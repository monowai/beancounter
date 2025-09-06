package com.beancounter.marketdata.mcp

import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Portfolio
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.providers.PriceService
import com.beancounter.marketdata.trn.TrnService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * MCP Server for Beancounter Data Service
 *
 * Exposes market data, asset management, portfolio, and transaction functionality
 * through the Model Context Protocol for AI integration. Uses actual business services.
 */
@Service
class DataMcpServer(
    private val assetService: AssetService,
    private val portfolioService: PortfolioService,
    private val priceService: PriceService,
    private val trnService: TrnService,
    private val fxRateService: FxRateService,
    private val marketService: MarketService,
    private val currencyService: CurrencyService
) {
    private val log = LoggerFactory.getLogger(DataMcpServer::class.java)

    /**
     * Find or create an asset by market and code
     */
    fun findOrCreateAsset(
        market: String,
        code: String,
        category: String = "EQUITY"
    ): Asset {
        log.info("MCP: Finding/creating asset - market: {}, code: {}", market, code)
        val assetInput = AssetInput(market, code, category = category)
        return assetService.findOrCreate(assetInput)
    }

    /**
     * Get asset information by asset ID
     */
    fun getAsset(assetId: String): Asset {
        log.info("MCP: Getting asset by ID: {}", assetId)
        return assetService.find(assetId)
    }

    /**
     * Get all portfolios for the current user
     */
    fun getPortfolios(): PortfoliosResponse {
        log.info("MCP: Getting all portfolios for current user")
        val portfolios = portfolioService.portfolios()
        return PortfoliosResponse(portfolios)
    }

    /**
     * Get a specific portfolio by ID
     */
    fun getPortfolio(portfolioId: String): Portfolio {
        log.info("MCP: Getting portfolio by ID: {}", portfolioId)
        return portfolioService.find(portfolioId)
    }

    /**
     * Get portfolio by code
     */
    fun getPortfolioByCode(code: String): Portfolio {
        log.info("MCP: Getting portfolio by code: {}", code)
        return portfolioService.findByCode(code)
    }

    /**
     * Get portfolios where a specific asset is held
     */
    fun getPortfoliosWhereHeld(
        assetId: String,
        tradeDate: String? = null
    ): PortfoliosResponse {
        log.info("MCP: Getting portfolios where asset {} is held", assetId)
        val date = if (tradeDate != null) LocalDate.parse(tradeDate) else null
        return portfolioService.findWhereHeld(assetId, date)
    }

    /**
     * Get transactions for a portfolio
     */
    fun getTransactionsForPortfolio(
        portfolioId: String,
        tradeDate: String? = null
    ): Collection<com.beancounter.common.model.Trn> {
        log.info("MCP: Getting transactions for portfolio: {}", portfolioId)
        val date = if (tradeDate != null) LocalDate.parse(tradeDate) else LocalDate.now()
        return trnService.findForPortfolio(portfolioId, date)
    }

    /**
     * Get market data for an asset on a specific date
     */
    fun getMarketData(
        assetId: String,
        date: String
    ): Map<String, Any> {
        log.info("MCP: Getting market data for asset: {} on date: {}", assetId, date)
        val priceDate = LocalDate.parse(date)
        val marketData = priceService.getMarketData(assetId, priceDate)

        return if (marketData.isPresent) {
            val md = marketData.get()
            mapOf(
                "assetId" to assetId,
                "date" to date,
                "close" to md.close,
                "open" to md.open,
                "high" to md.high,
                "low" to md.low,
                "volume" to md.volume,
                "dividend" to (md.dividend),
                "split" to (md.split)
            )
        } else {
            mapOf(
                "assetId" to assetId,
                "date" to date,
                "error" to "No market data found for the specified date"
            )
        }
    }

    /**
     * Get FX rates between currencies
     */
    fun getFxRates(
        fromCurrency: String,
        toCurrency: String,
        rateDate: String? = null
    ): FxResponse {
        log.info("MCP: Getting FX rates from {} to {}", fromCurrency, toCurrency)
        val date = rateDate ?: "today"
        val fxRequest = FxRequest(rateDate = date)
        fxRequest.add(IsoCurrencyPair(fromCurrency, toCurrency))
        return fxRateService.getRates(fxRequest, "system")
    }

    /**
     * Get all available markets
     */
    fun getMarkets(): MarketResponse {
        log.info("MCP: Getting all available markets")
        return marketService.getMarkets()
    }

    /**
     * Get all available currencies
     */
    fun getCurrencies(): Collection<com.beancounter.common.model.Currency> {
        log.info("MCP: Getting all available currencies")
        return currencyService.currencies().toList()
    }

    /**
     * Get current market data for multiple assets
     */
    fun getCurrentPrices(
        assetIds: String,
        date: String? = null
    ): Map<String, Any> {
        log.info("MCP: Getting current prices for assets: {}", assetIds)
        val assetIdList = assetIds.split(",").map { it.trim() }
        val priceDate = if (date != null) LocalDate.parse(date) else LocalDate.now()

        val assets = assetIdList.map { assetService.find(it) }
        val marketDataList = priceService.getMarketData(assets, priceDate)

        return mapOf(
            "date" to priceDate.toString(),
            "prices" to
                marketDataList.map { md ->
                    mapOf(
                        "assetId" to md.asset.id,
                        "assetCode" to md.asset.code,
                        "market" to md.asset.market.code,
                        "close" to md.close,
                        "dividend" to md.dividend,
                        "split" to md.split
                    )
                }
        )
    }

    /**
     * Get all available MCP tools/functions exposed by this service
     */
    fun getAvailableTools(): Map<String, String> =
        mapOf(
            "find_or_create_asset" to "Find or create an asset by market and code",
            "get_asset" to "Get asset information by asset ID",
            "get_portfolios" to "Get all portfolios for the current user",
            "get_portfolio" to "Get a specific portfolio by ID",
            "get_portfolio_by_code" to "Get portfolio by code",
            "get_portfolios_where_held" to "Get portfolios where a specific asset is held",
            "get_transactions_for_portfolio" to "Get transactions for a portfolio",
            "get_market_data" to "Get market data for an asset on a specific date",
            "get_fx_rates" to "Get FX rates between currencies",
            "get_markets" to "Get all available markets",
            "get_currencies" to "Get all available currencies",
            "get_current_prices" to "Get current market data for multiple assets"
        )
}