package com.beancounter.marketdata.providers.wtd

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PreviousClosePriceDate
import com.beancounter.marketdata.config.MarketConfig
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
class WtdConfig : DataProviderConfig {

    @Value("\${beancounter.market.providers.WTD.batchSize:2}")
    var assetsPerRequest = 2

    // For testing purposes - allows us to setup a static base date for which Market Prices Dates
    // can reliably computed from.
    @Value("\${beancounter.market.providers.WTD.date:#{null}}")
    var date: String? = null
        get() = if (field == null) dateUtils.today() else field

    @Value("\${beancounter.market.providers.WTD.markets}")
    var markets: String? = null

    @set:Autowired
    var marketConfig: MarketConfig? = null
    final var dateUtils = DateUtils()
    var marketUtils = PreviousClosePriceDate(dateUtils)

    private fun translateMarketCode(market: Market): String? {
        return (marketConfig!!.getProviders()[market.code] ?: error("Missing Market")).aliases[WtdService.ID]
    }

    override fun getMarketDate(market: Market, date: String): LocalDate {
        var daysToSubtract = 0
        if (dateUtils.isToday(date)) {
            // If Current, price date is T-daysToSubtract
            daysToSubtract = 1
            if (market.code.equals("NZX", ignoreCase = true)) {
                daysToSubtract = 2
            }
        }

        // If startDate is not "today", assume nothing.  Discount the weekends
        return marketUtils.getPriceDate(
            dateUtils.getDate(date).atStartOfDay(), daysToSubtract
        )
    }

    override fun getPriceCode(asset: Asset): String {
        val marketCode = translateMarketCode(asset.market)
        return if (marketCode != null && marketCode.isNotEmpty()) {
            asset.code + "." + marketCode
        } else asset.code
    }

    override fun getBatchSize(): Int {
        return assetsPerRequest
    }
}
