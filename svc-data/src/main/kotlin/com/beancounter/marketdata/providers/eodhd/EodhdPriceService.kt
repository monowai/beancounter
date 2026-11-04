package com.beancounter.marketdata.providers.eodhd

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.providers.MarketDataPriceProvider
import com.beancounter.marketdata.providers.logApiKeyStatus
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Facade for the EODHD market data provider — see https://eodhd.com.
 *
 * EODHD is opt-in: it ships disabled by default (empty `markets` allowlist, `key=demo`) so registered
 * AlphaVantage / MarketStack / Morningstar routing is unchanged until an operator explicitly enables
 * markets via `BEANCOUNTER_MARKET_PROVIDERS_EODHD_MARKETS`. End-of-day prices come from
 * [EodhdDailyPrices], live quotes from [EodhdQuotes] and asset search from [EodhdAssetSearch].
 */
@Service
class EodhdPriceService(
    private val eodhdConfig: EodhdConfig,
    private val eodhdDailyPrices: EodhdDailyPrices,
    private val eodhdQuotes: EodhdQuotes
) : MarketDataPriceProvider {
    private val log = LoggerFactory.getLogger(EodhdPriceService::class.java)

    @PostConstruct
    fun logStatus() = logApiKeyStatus(log, "BEANCOUNTER_MARKET_PROVIDERS_EODHD_KEY", eodhdConfig.apiKey)

    override fun getMarketData(priceRequest: PriceRequest): Collection<MarketData> =
        eodhdDailyPrices.getMarketData(priceRequest)

    override fun getId(): String = ID

    override fun isMarketSupported(market: Market): Boolean =
        eodhdConfig.supportsMarketCode(eodhdConfig.markets, market.code)

    override fun getDate(
        market: Market,
        priceRequest: PriceRequest
    ): LocalDate =
        eodhdConfig.getMarketDate(
            market,
            priceRequest.date
        )

    override fun backFill(
        asset: Asset,
        fromDate: LocalDate
    ): PriceResponse = eodhdDailyPrices.backFill(asset, fromDate)

    override fun isApiSupported(): Boolean = true

    // EODHD `/api/eod` ships `adjusted_close` per row; `EodhdAdapter` persists
    // that value as `MarketData.close` (see PR #875). SplitAdjuster must skip
    // dividing EODHD rows so corporate_event splits don't double-adjust them.
    override fun shipsAdjustedClose(): Boolean = true

    /**
     * Live (delayed) quote from `/api/real-time`; `null` when EODHD has none, so the caller falls
     * back to the stored close.
     */
    override fun getQuote(asset: Asset): MarketData? = eodhdQuotes.getQuote(asset)

    override fun getQuotes(assets: Collection<Asset>): Map<String, MarketData> = eodhdQuotes.getQuotes(assets)

    companion object {
        const val ID = "EODHD"
    }
}