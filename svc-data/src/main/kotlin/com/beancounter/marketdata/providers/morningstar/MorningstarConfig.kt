package com.beancounter.marketdata.providers.morningstar

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
 * Configuration for the Morningstar market data provider.
 * Handles UK/EU mutual funds that are not available via Alpha Vantage.
 */
@Configuration
@Import(
    MorningstarPriceService::class,
    MorningstarProxy::class
)
class MorningstarConfig(
    val dateUtils: DateUtils = DateUtils(),
    val marketUtils: PreviousClosePriceDate = PreviousClosePriceDate(DateUtils())
) : DataProviderConfig {
    @Value("\${beancounter.market.providers.morningstar.markets:MUTF}")
    var markets: String = "MUTF"

    companion object {
        const val ID = "MORNINGSTAR"

        // Morningstar API endpoint (publicly accessible)
        const val PRICE_API_URL = "https://tools.morningstar.co.uk/api/rest.svc/timeseries_price/t92wz0sj7c"
    }

    override fun getBatchSize() = 1

    fun isMarketSupported(market: Market): Boolean = markets.contains(market.code, ignoreCase = true)

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

    override fun getPriceCode(asset: Asset): String = asset.priceSymbol ?: asset.code
}