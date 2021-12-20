package com.beancounter.marketdata.providers.cash

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.providers.MarketDataProvider
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Cash still has a price, and we need to be able to resolve it for valuation reasons
 *
 * @author mikeh
 * @since 2021-12-01
 */
@Service
class CashProviderService : MarketDataProvider {
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
        return ID.equals(market.code, ignoreCase = true)
    }

    val priceDate: LocalDate?
        get() = dateUtils.getLocalDate()

    override fun getDate(market: Market, priceRequest: PriceRequest): LocalDate {
        return priceDate!!
    }

    override fun backFill(asset: Asset): PriceResponse {
        throw UnsupportedOperationException("Cash does not support backfill requests")
    }

    companion object {
        const val ID = "CASH"
    }
}
