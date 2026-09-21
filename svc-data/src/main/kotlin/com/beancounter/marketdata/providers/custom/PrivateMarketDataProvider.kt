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
    // ACCOUNT and POLICY (retirement fund) assets are treated like cash - always price = 1
    private fun isCashLike(asset: Asset): Boolean =
        asset.assetCategory.id == AssetCategory.ACCOUNT || asset.assetCategory.id == AssetCategory.POLICY

    private fun resolvePrice(
        asset: Asset,
        closest: MarketData?,
        defaultPrice: BigDecimal
    ): MarketData {
        if (isCashLike(asset)) {
            return MarketData(
                asset,
                priceDate,
                close = BigDecimal.ONE
            )
        }

        return if (closest != null) {
            getMarketData(
                asset,
                closest
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

    /**
     * DATA-6G: this provider runs on every `POST /api/prices` call that includes a
     * PRIVATE-market asset (real estate, art, accounts, policies) - not just the
     * market-closed fallback the sibling fix in `MarketDataPriceProcessor` covers.
     * It used to issue one `findTop1By...LessThanEqual` query per asset needing a
     * carried-forward price; the lookup for the whole request is now a single
     * batched query.
     */
    override fun getMarketData(priceRequest: PriceRequest): List<MarketData> {
        val assets = priceRequest.assets.mapNotNull { it.resolvedAsset }
        if (assets.isEmpty()) return emptyList()

        val lookupAssets = assets.filterNot(::isCashLike)
        val closestByAssetId =
            if (lookupAssets.isEmpty()) {
                emptyMap()
            } else {
                marketDataRepo
                    .findLatestByAssetInAndPriceDateLessThanEqual(lookupAssets, priceDate)
                    .associateBy { it.asset.id }
            }

        return assets.map { asset ->
            resolvePrice(asset, closestByAssetId[asset.id], priceRequest.closePrice)
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

    override fun backFill(
        asset: Asset,
        fromDate: LocalDate
    ): PriceResponse = throw UnsupportedOperationException("Private market assets do not support backfill requests")

    override fun isApiSupported(): Boolean = false

    companion object {
        const val ID = "PRIVATE"
    }
}