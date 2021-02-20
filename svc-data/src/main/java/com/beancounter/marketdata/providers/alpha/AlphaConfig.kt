package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.MarketUtils
import com.beancounter.marketdata.providers.DataProviderConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.time.LocalDate

@Configuration
@Import(AlphaService::class, AlphaProxyCache::class, AlphaPriceAdapter::class)
class AlphaConfig : DataProviderConfig {

    @Value("\${beancounter.market.providers.ALPHA.markets}")
    var markets: String? = null
    var dateUtils = DateUtils()
    var marketUtils = MarketUtils(dateUtils)

    override fun getBatchSize(): Int {
        return 1
    }

    fun translateMarketCode(market: Market): String? {
        if (market.code.equals("NASDAQ", ignoreCase = true) ||
            market.code.equals("NYSE", ignoreCase = true) ||
            market.code.equals("LON", ignoreCase = true) ||
            market.code.equals("AMEX", ignoreCase = true)
        ) {
            return null
        }
        return if (market.code.equals("ASX", ignoreCase = true)) {
            "AX"
        } else market.code
    }

    override fun getMarketDate(market: Market, date: String): LocalDate {
        return marketUtils.getLastMarketDate(dateUtils.getDate(date)!!, market)
    }

    override fun getPriceCode(asset: Asset): String {
        if (asset.priceSymbol != null) {
            return asset.priceSymbol!!
        }
        val marketCode = translateMarketCode(asset.market)
        return if (marketCode != null && marketCode.isNotEmpty()) {
            asset.code + "." + marketCode
        } else asset.code
    }

    fun translateSymbol(code: String): String {
        return code.replace(".", "-")
    }
}
