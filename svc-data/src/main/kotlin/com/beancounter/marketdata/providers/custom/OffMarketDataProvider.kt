package com.beancounter.marketdata.providers.custom

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.providers.MarketDataPriceProvider
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Investment assets that are unique to the user - Real Estate, Art, and other assets that can not be priced by
 * an external market data provider
 *
 * @author mikeh
 * @since 2021-12-01
 */
@Service
class OffMarketDataProvider : MarketDataPriceProvider {
    private val dateUtils = DateUtils()
    private fun getMarketData(asset: Asset): MarketData {
        val result = MarketData(asset, priceDate!!)
        result.close = BigDecimal.ONE
        return result
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

    val priceDate: LocalDate?
        get() = dateUtils.getLocalDate()

    override fun getDate(market: Market, priceRequest: PriceRequest): LocalDate {
        return priceDate!!
    }

    override fun backFill(asset: Asset): PriceResponse {
        throw UnsupportedOperationException("Custom assets do not support backfill requests")
    }

    companion object {
        const val ID = "CUSTOM"
    }
}
