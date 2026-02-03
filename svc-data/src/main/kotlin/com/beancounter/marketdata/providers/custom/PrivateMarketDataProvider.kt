package com.beancounter.marketdata.providers.custom

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.providers.MarketDataPriceProvider
import com.beancounter.marketdata.providers.MarketDataRepo
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
class PrivateMarketDataProvider(
    val marketDataRepo: MarketDataRepo,
    val dateUtils: DateUtils
) : MarketDataPriceProvider {
    private fun getMarketData(
        asset: Asset,
        defaultPrice: BigDecimal
    ): MarketData {
        // ACCOUNT and POLICY (retirement fund) assets are treated like cash - always price = 1
        if (asset.assetCategory.id == AssetCategory.ACCOUNT ||
            asset.assetCategory.id == AssetCategory.POLICY
        ) {
            return MarketData(
                asset,
                priceDate,
                close = BigDecimal.ONE
            )
        }

        val closest =
            marketDataRepo.findTop1ByAssetAndPriceDateLessThanEqual(
                asset,
                priceDate
            )
        return if (closest.isPresent) {
            getMarketData(
                asset,
                closest.get()
            )
        } else {
            MarketData(
                asset,
                priceDate,
                close = defaultPrice
            )
        }
    }

    fun getMarketData(
        asset: Asset,
        from: MarketData
    ): MarketData =
        MarketData(
            asset,
            close = from.close,
            priceDate = priceDate
        )

    override fun getMarketData(priceRequest: PriceRequest): List<MarketData> =
        priceRequest.assets.mapNotNull { (_, _, resolvedAsset) ->
            resolvedAsset?.let {
                getMarketData(
                    it,
                    priceRequest.closePrice
                )
            }
        }

    override fun getId(): String = ID

    override fun isMarketSupported(market: Market): Boolean =
        getId().equals(
            market.code,
            ignoreCase = true
        )

    val priceDate: LocalDate
        get() = dateUtils.getDate()

    override fun getDate(
        market: Market,
        priceRequest: PriceRequest
    ): LocalDate = dateUtils.getFormattedDate(priceRequest.date)

    override fun backFill(asset: Asset): PriceResponse =
        throw UnsupportedOperationException("Private market assets do not support backfill requests")

    override fun isApiSupported(): Boolean = false

    companion object {
        const val ID = "PRIVATE"
    }
}