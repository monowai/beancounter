package com.beancounter.marketdata.providers.custom

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.providers.MarketDataPriceProvider
import com.beancounter.marketdata.providers.MarketDataRepo
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Investment assets that are unique to the user - Real Estate, Art, and other assets that can not be priced by
 * an external market data provider
 *
 * @author mikeh
 * @since 2021-12-01
 */
@Service
class OffMarketDataProvider(
    val marketDataRepo: MarketDataRepo,
    val dateUtils: DateUtils,
) : MarketDataPriceProvider {
    private fun getMarketData(asset: Asset): MarketData {
        val closest = marketDataRepo.findTop1ByAssetAndPriceDateLessThanEqual(asset, priceDate)
        return if (closest.isPresent) getMarketData(asset, closest.get()) else MarketData(asset, priceDate)
    }

    fun getMarketData(
        asset: Asset,
        from: MarketData,
    ): MarketData {
        return MarketData(asset, close = from.close, priceDate = priceDate)
    }

    override fun getMarketData(priceRequest: PriceRequest): Collection<MarketData> {
        val results: MutableCollection<MarketData> = ArrayList(priceRequest.assets.size)
        for ((_, _, resolvedAsset) in priceRequest.assets) {
            if (resolvedAsset != null) {
                results.add(getMarketData(resolvedAsset))
            }
        }
        return results
    }

    override fun getId(): String {
        return ID
    }

    override fun isMarketSupported(market: Market): Boolean {
        return getId().equals(market.code, ignoreCase = true)
    }

    val priceDate: LocalDate
        get() = dateUtils.getDate()

    override fun getDate(
        market: Market,
        priceRequest: PriceRequest,
    ): LocalDate {
        return dateUtils.getFormattedDate(priceRequest.date)
    }

    override fun backFill(asset: Asset): PriceResponse {
        throw UnsupportedOperationException("Custom assets do not support backfill requests")
    }

    companion object {
        const val ID = "OFFM"
    }
}
