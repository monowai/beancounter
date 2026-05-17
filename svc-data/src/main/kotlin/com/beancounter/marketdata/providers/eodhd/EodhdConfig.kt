package com.beancounter.marketdata.providers.eodhd

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PreviousClosePriceDate
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.DataProviderConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.time.LocalDate

/**
 * Configuration settings for the EODHD market data provider.
 *
 * EODHD is opt-in: with the default empty `markets` allowlist, [EodhdPriceService.isMarketSupported]
 * returns false for every market, so registered AlphaVantage / MarketStack / Morningstar routing is
 * unchanged. Operators enable specific markets by setting `beancounter.market.providers.eodhd.markets`.
 */
@Configuration
@EnableConfigurationProperties(EodhdNewsProperties::class)
@Import(
    EodhdPriceService::class,
    EodhdProxy::class,
    EodhdAdapter::class,
    EodhdEventService::class,
    EodhdNewsService::class
)
class EodhdConfig(
    val marketService: MarketService
) : DataProviderConfig {
    @Value($$"${beancounter.market.providers.eodhd.batchSize:1}")
    var assetsPerRequest = 1

    @Value($$"${beancounter.market.providers.eodhd.markets:}")
    var markets: String? = null

    @Value($$"${beancounter.market.providers.eodhd.key:demo}")
    lateinit var apiKey: String

    final var dateUtils = DateUtils()
    var marketUtils = PreviousClosePriceDate(dateUtils)

    private fun translateMarketCode(market: Market): String? =
        marketService
            .getMarket(market.code)
            .getAlias(EodhdPriceService.ID)

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

    /**
     * Compose the EODHD ticker for this asset.
     *
     * EODHD always wants `${code}.${exchange}` — even US tickers use the `.US` suffix.
     * The `eodhd:` alias on each market in `application.yml` supplies the suffix (US, LSE, AU, TO, …).
     * If an asset already carries a [Asset.priceSymbol], honour that as an explicit override.
     */
    override fun getPriceCode(asset: Asset): String {
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
}