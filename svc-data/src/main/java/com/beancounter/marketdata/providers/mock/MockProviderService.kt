package com.beancounter.marketdata.providers.mock

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.service.MarketDataProvider
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

/**
 * For testing purposes. Part of the main source in order to allow for an off-line provider
 * that will force certain error conditions.
 *
 * @author mikeh
 * @since 2019-03-01
 */
@Service
class MockProviderService : MarketDataProvider {
    private val dateUtils = DateUtils()
    private fun getMarketData(asset: Asset): MarketData {
        if (asset.code.equals("123", ignoreCase = true)) {
            throw BusinessException(String.format("Invalid asset code [%s]", asset.code))
        }
        val result = MarketData(asset, priceDate!!)
        result.close = BigDecimal.valueOf(999.99)
        result.open = BigDecimal.valueOf(999.99)
        return result
    }

    override fun getMarketData(priceRequest: PriceRequest): Collection<MarketData> {
        val results: MutableCollection<MarketData> = ArrayList(priceRequest.assets.size)
        for ((_, _, _, resolvedAsset) in priceRequest.assets) {
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
        return "MOCK".equals(market.code, ignoreCase = true)
    }

    val priceDate: LocalDate?
        get() = dateUtils.getDate("2019-11-21")

    override fun getDate(market: Market, priceRequest: PriceRequest): LocalDate {
        return priceDate!!
    }

    override fun backFill(asset: Asset): PriceResponse {
        return PriceResponse()
    }

    companion object {
        const val ID = "MOCK"
    }
}
