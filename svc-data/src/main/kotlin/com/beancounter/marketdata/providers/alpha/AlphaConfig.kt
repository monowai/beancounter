package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PreviousClosePriceDate
import com.beancounter.marketdata.providers.DataProviderConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.time.LocalDate

/**
 * Helper functions for Alpha data provider. Enable dependant supporting classes
 */
@Configuration
@Import(AlphaPriceService::class, AlphaProxyCache::class, AlphaPriceAdapter::class)
class AlphaConfig : DataProviderConfig {

    @Value("\${beancounter.market.providers.ALPHA.markets}")
    var markets: String? = null
    final var dateUtils = DateUtils()
    var marketUtils = PreviousClosePriceDate(dateUtils)

    override fun getBatchSize(): Int {
        return 1
    }

    fun translateMarketCode(market: Market): String? {
        if (isNullMarket(market.code)) {
            return null
        }
        return if (market.code.equals("ASX", ignoreCase = true)) {
            "AX"
        } else {
            market.code
        }
    }

    val nullMarket = "NASDAQ|NYSE|AMEX|US"
    fun isNullMarket(marketCode: String): Boolean {
        return nullMarket.contains(marketCode, true)
    }

    override fun getMarketDate(market: Market, date: String, currentMode: Boolean): LocalDate {
        return marketUtils.getPriceDate(
            dateUtils.offsetNow(date),
            market,
            currentMode,
        )
    }

    override fun getPriceCode(asset: Asset): String {
        if (asset.priceSymbol != null) {
            return asset.priceSymbol!!
        }
        val marketCode = translateMarketCode(asset.market)
        return if (!marketCode.isNullOrEmpty()) {
            asset.code + "." + marketCode
        } else {
            asset.code
        }
    }

    fun translateSymbol(code: String): String {
        return code.replace(".", "-")
    }
}
