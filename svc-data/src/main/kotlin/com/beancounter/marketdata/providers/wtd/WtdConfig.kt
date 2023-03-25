package com.beancounter.marketdata.providers.wtd

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
 * Configuration settings for the now defunct WorldTrading Data.  Convert to MarketStack!
 */
@Configuration
@Import(WtdService::class, WtdProxy::class, WtdAdapter::class)
class WtdConfig @Autowired constructor(val marketService: MarketService) : DataProviderConfig {

    @Value("\${beancounter.market.providers.WTD.batchSize:2}")
    var assetsPerRequest = 2

    // For testing purposes - allows us to set up a static base date for which Market Prices Dates
    // can be reliably computed from.
    @Value("\${beancounter.market.providers.WTD.date:#{null}}")
    var date: String? = null
        get() = if (field == null) dateUtils.today() else field

    @Value("\${beancounter.market.providers.WTD.markets}")
    var markets: String? = null

    final var dateUtils = DateUtils()
    var marketUtils = PreviousClosePriceDate(dateUtils)

    private fun translateMarketCode(market: Market): String? {
        return marketService.getMarket(market.code).aliases[WtdService.ID]
    }

    override fun getMarketDate(market: Market, date: String, currentMode: Boolean): LocalDate {
        return marketUtils.getPriceDate(
            dateUtils.offsetNow(date),
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
}
