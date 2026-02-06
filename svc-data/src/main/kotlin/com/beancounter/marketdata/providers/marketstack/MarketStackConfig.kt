package com.beancounter.marketdata.providers.marketstack

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PreviousClosePriceDate
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.DataProviderConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.time.LocalDate

/**
 * Configuration settings for MarketStack Data.
 */
@Configuration
@Import(
    MarketStackService::class,
    MarketStackProxy::class,
    MarketStackAdapter::class
)
class MarketStackConfig(
    val marketService: MarketService
) : DataProviderConfig {
    @Value($$"${beancounter.market.providers.mstack.batchSize:2}")
    var assetsPerRequest = 2

    // For testing purposes - allows us to set up a static base date for which Market Prices Dates
    // can be reliably computed from.
    @Value($$"${beancounter.market.providers.mstack.date:#{null}}")
    var date: String? = null
        get() = if (field == null) dateUtils.today() else field

    @Value($$"${beancounter.market.providers.mstack.markets}")
    var markets: String? = null

    @Value($$"${beancounter.market.providers.mstack.key:demo}")
    lateinit var apiKey: String

    final var dateUtils = DateUtils()
    var marketUtils = PreviousClosePriceDate(dateUtils)

    private fun translateMarketCode(market: Market): String? =
        marketService
            .getMarket(market.code)
            .getAlias(MarketStackService.ID)

    override fun getMarketDate(
        market: Market,
        date: String,
        currentMode: Boolean
    ): LocalDate =
        marketUtils.getPriceDate(
            dateUtils.offsetNow(date).toZonedDateTime(),
            market,
            currentMode
        )

    override fun getPriceCode(asset: Asset): String {
        // Use priceSymbol if set (e.g., "D05.SI" for FIGI ticker "DBS")
        if (!asset.priceSymbol.isNullOrEmpty()) {
            return asset.priceSymbol!!
        }
        val marketCode = translateMarketCode(asset.market)
        return if (!marketCode.isNullOrEmpty()) {
            asset.code + "." + marketCode
        } else {
            asset.code
        }
    }

    override fun getBatchSize(): Int = assetsPerRequest

    /**
     * MarketStack configuration constants.
     */
    companion object {
        const val DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ"

        // MarketStack V2 exchange MIC codes for the tickers search endpoint.
        // The mstack alias stores the V2 price suffix (e.g., "SI"), but the
        // exchange tickers endpoint needs the MIC code (e.g., "XSES").
        private val micCodes =
            mapOf(
                "US" to "XNAS",
                "NASDAQ" to "XNAS",
                "NYSE" to "XNYS",
                "SGX" to "XSES",
                "NZX" to "XNZE",
                "ASX" to "XASX"
            )

        fun getMicCode(market: String): String? = micCodes[market.uppercase()]
    }
}