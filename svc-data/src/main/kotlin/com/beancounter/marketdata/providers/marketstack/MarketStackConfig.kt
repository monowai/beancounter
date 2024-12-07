package com.beancounter.marketdata.providers.marketstack

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PreviousClosePriceDate
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.DataProviderConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.time.LocalDate

/**
 * Configuration settings for MarketStack Data.
 */
@Configuration
@Import(MarketStackService::class, MarketStackProxy::class, MarketStackAdapter::class)
class MarketStackConfig
@Autowired
constructor(val marketService: MarketService) : DataProviderConfig {
    @Value("\${beancounter.market.providers.mstack.batchSize:2}")
    var assetsPerRequest = 2

    // For testing purposes - allows us to set up a static base date for which Market Prices Dates
    // can be reliably computed from.
    @Value("\${beancounter.market.providers.mstack.date:#{null}}")
    var date: String? = null
        get() = if (field == null) dateUtils.today() else field

    @Value("\${beancounter.market.providers.mstack.markets}")
    var markets: String? = null

    @Value("\${beancounter.market.providers.mstack.key:demo}")
    lateinit var apiKey: String

    final var dateUtils = DateUtils()
    var marketUtils = PreviousClosePriceDate(dateUtils)

    private fun translateMarketCode(market: Market): String? {
        return marketService.getMarket(market.code)
            .getAlias(MarketStackService.ID)
    }

    override fun getMarketDate(
        market: Market,
        date: String,
        currentMode: Boolean,
    ): LocalDate {
        return marketUtils.getPriceDate(
            dateUtils.offsetNow(date).toZonedDateTime(),
            market,
            currentMode,
        )
    }

    override fun getPriceCode(asset: Asset): String {
        val marketCode = translateMarketCode(asset.market)
        return if (!marketCode.isNullOrEmpty()) {
            asset.code + "." + marketCode
        } else {
            asset.code
        }
    }

    override fun getBatchSize(): Int {
        return assetsPerRequest
    }

    companion object {
        const val DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ"
    }
}
