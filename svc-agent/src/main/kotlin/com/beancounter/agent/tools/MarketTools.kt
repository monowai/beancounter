package com.beancounter.agent.tools

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.MarketService
import com.beancounter.client.services.StaticService
import com.beancounter.common.contracts.CurrencyResponse
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.model.IsoCurrencyPair
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service

/**
 * Tools for static reference data (markets, currencies) and FX rates.
 */
@Service
class MarketTools(
    private val marketService: MarketService,
    private val staticService: StaticService,
    private val fxService: FxService,
    private val tokenService: TokenService
) {
    @Tool(description = MARKETS_DESC)
    fun listMarkets(): MarketResponse = marketService.getMarkets()

    @Tool(description = "List every supported ISO currency.")
    fun listCurrencies(): CurrencyResponse = staticService.currencies

    @Tool(description = FX_DESC)
    fun getFxRate(
        @ToolParam(description = "Source ISO currency code, e.g. USD") fromCurrency: String,
        @ToolParam(description = "Target ISO currency code, e.g. NZD") toCurrency: String,
        @ToolParam(description = "Rate date YYYY-MM-DD or 'today'") rateDate: String = "today"
    ): FxResponse {
        val request =
            FxRequest(
                rateDate = rateDate,
                pairs = mutableSetOf(IsoCurrencyPair(fromCurrency, toCurrency))
            )
        return fxService.getRates(request, tokenService.bearerToken)
    }

    companion object {
        const val MARKETS_DESC =
            "List every market Beancounter knows about (NASDAQ, NYSE, ASX, NZX, etc.) " +
                "with its trading currency and timezone."
        const val FX_DESC =
            "Get the foreign exchange rate between two ISO currency codes on a given date. " +
                "Returns the rate, the rate date and the provider."
    }
}