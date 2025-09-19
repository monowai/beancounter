package com.beancounter.agent.client

import com.beancounter.agent.config.FeignAuthInterceptor
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * Feign client for Data MCP service
 */
@FeignClient(
    name = "data-mcp",
    url = "\${marketdata.url}",
    path = "/api/mcp",
    configuration = [FeignAuthInterceptor::class]
)
interface DataMcpClient {
    @GetMapping("/ping")
    fun ping(): Map<String, String>

    @GetMapping("/portfolio/{portfolioId}")
    fun getPortfolio(
        @PathVariable portfolioId: String
    ): Portfolio

    @GetMapping("/portfolio/code/{code}")
    fun getPortfolioByCode(
        @PathVariable code: String
    ): Portfolio

    @GetMapping("/portfolios")
    fun getPortfolios(): PortfoliosResponse

    @PostMapping("/asset/find-or-create")
    fun findOrCreateAsset(
        @RequestParam market: String,
        @RequestParam code: String,
        @RequestParam category: String
    ): Asset

    @GetMapping("/asset/{assetId}")
    fun getAsset(
        @PathVariable assetId: String
    ): Asset

    @GetMapping("/asset/{assetId}/market-data")
    fun getMarketData(
        @PathVariable assetId: String,
        @RequestParam date: String
    ): Map<String, Any>

    @GetMapping("/fx-rates")
    fun getFxRates(
        @RequestParam fromCurrency: String,
        @RequestParam toCurrency: String,
        @RequestParam(required = false) rateDate: String?
    ): FxResponse

    @GetMapping("/markets")
    fun getMarkets(): MarketResponse

    @GetMapping("/currencies")
    fun getCurrencies(): Array<Currency>
}