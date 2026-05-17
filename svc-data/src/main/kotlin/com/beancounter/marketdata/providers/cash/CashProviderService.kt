package com.beancounter.marketdata.providers.cash

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
 * Cash still has a price, and we need to be able to resolve it for valuation reasons
 *
 * @author mikeh
 * @since 2021-12-01
 */
@Service
class CashProviderService(
    val dateUtils: DateUtils = DateUtils()
) : MarketDataPriceProvider {
    private fun getMarketData(asset: Asset): MarketData {
        val result =
            MarketData(
                asset,
                priceDate!!
            )
        result.close = BigDecimal.ONE
        return result
    }

    override fun getMarketData(priceRequest: PriceRequest): Collection<MarketData> {
        val results: MutableCollection<MarketData> = ArrayList(priceRequest.assets.size)
        for ((_, _, resolvedAsset, _) in priceRequest.assets) {
            if (resolvedAsset != null) {
                results.add(getMarketData(resolvedAsset))
            }
        }
        return results
    }

    override fun getId(): String = ID

    override fun isMarketSupported(market: Market): Boolean =
        ID.equals(
            market.code,
            ignoreCase = true
        )

    override fun isApiSupported(): Boolean = false

    val priceDate: LocalDate?
        get() = dateUtils.getDate()

    /**
     * Retrieve the provider's configured price date.
     *
     * Both `market` and `priceRequest` are accepted to satisfy the provider interface but are not used.
     *
     * @param market Ignored.
     * @param priceRequest Ignored.
     * @return The provider's current price date.
     */
    override fun getDate(
        market: Market,
        priceRequest: PriceRequest
    ): LocalDate = priceDate!!

    /**
     * Rejects backfill requests for cash assets.
     *
     * @param asset The cash asset for which backfill was requested.
     * @param fromDate The start date (inclusive) from which backfill was requested.
     * @throws UnsupportedOperationException Always thrown because cash does not support backfill requests.
     */
    override fun backFill(
        asset: Asset,
        fromDate: LocalDate
    ): PriceResponse = throw UnsupportedOperationException("Cash does not support backfill requests")

    companion object {
        const val ID = "CASH"
    }
}