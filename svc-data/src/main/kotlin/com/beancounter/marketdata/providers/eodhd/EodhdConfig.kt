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
    // Bulk endpoint groups up to N symbols per HTTP round-trip (see
    // `EodhdPriceService.fetchBulk`). Default sized for the scheduled
    // PortfolioValuationSchedule fan-out — covers a typical kauri valuation
    // cycle (~14 unique held US tickers) in a single bulk call instead of N
    // serial `/api/eod/{symbol}` round-trips. Raise via env var if a single
    // portfolio holds more positions than this.
    @Value($$"${beancounter.market.providers.eodhd.batchSize:50}")
    var assetsPerRequest = 50

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

    /**
     * EODHD exchange code for [market], resolved via `MarketService` so the alias map
     * configured in `application.yml` is honoured. Falls back to the raw market code
     * when no `eodhd` alias is configured. Public to support bulk-endpoint routing in
     * [EodhdPriceService.fetchBulk] where the exchange goes in the URL path.
     */
    fun getExchange(market: Market): String =
        translateMarketCode(market).takeUnless { it.isNullOrEmpty() } ?: market.code

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