package com.beancounter.agent.tools

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.MarketService
import com.beancounter.client.services.PriceService
import com.beancounter.client.services.StaticService
import com.beancounter.common.contracts.CurrencyResponse
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.model.IsoCurrencyPair
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Tools for static reference data (markets, currencies), FX rates, and
 * single-asset spot price lookups for AI grounding.
 */
@Service
class MarketTools(
    private val marketService: MarketService,
    private val staticService: StaticService,
    private val fxService: FxService,
    private val tokenService: TokenService,
    private val priceService: PriceService
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

    @Tool(description = PRICE_DESC)
    fun getCurrentPrice(
        @ToolParam(description = "Market code, e.g. NASDAQ, NZX, ASX") market: String,
        @ToolParam(description = "Asset code as listed on that market, e.g. AAPL") code: String
    ): CurrentPrice {
        val response =
            priceService.getPrices(
                PriceRequest(
                    date = "today",
                    assets = listOf(PriceAsset(market = market, code = code)),
                    currentMode = true
                ),
                tokenService.bearerToken
            )
        val md =
            response.data.firstOrNull()
                ?: throw IllegalStateException("No price data for $market:$code")
        return CurrentPrice(
            assetCode = md.asset.code,
            market = md.asset.market.code,
            priceClose = md.close,
            previousClose = md.previousClose,
            changePercent = md.changePercent,
            priceDate = md.priceDate.toString()
        )
    }

    companion object {
        const val MARKETS_DESC =
            "List every market Beancounter knows about (NASDAQ, NYSE, ASX, NZX, etc.) " +
                "with its trading currency and timezone."
        const val FX_DESC =
            "Get the foreign exchange rate between two ISO currency codes on a given date. " +
                "Returns the rate, the rate date and the provider."
        const val PRICE_DESC =
            "Get the current public market price for a single asset (close, prior close, " +
                "intraday change %, price date). Use this to ground any forward-looking " +
                "claim — for example, when a news article cites an analyst price target, " +
                "call this to retrieve the current close so you can frame the implied " +
                "growth percentage relative to today's price. Never quote a price target " +
                "without also stating the current close."
    }
}

/**
 * Compact projection of a single asset's spot price returned to the LLM.
 * Mirrors the columnar fields the position tools already expose so the model
 * sees a consistent vocabulary across tool outputs.
 */
data class CurrentPrice(
    val assetCode: String,
    val market: String,
    val priceClose: BigDecimal,
    val previousClose: BigDecimal,
    val changePercent: BigDecimal,
    val priceDate: String
)