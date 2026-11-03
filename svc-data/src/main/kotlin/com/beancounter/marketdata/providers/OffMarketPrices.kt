package com.beancounter.marketdata.providers

import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.cache.CacheInvalidationProducer
import com.beancounter.marketdata.providers.custom.PrivateMarketDataProvider
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

/**
 * User-entered prices for assets no provider prices: the off-market write path.
 */
@Service
class OffMarketPrices(
    private val marketDataRepo: MarketDataRepo,
    private val assetFinder: AssetFinder,
    private val cacheInvalidationProducer: CacheInvalidationProducer? = null
) {
    /**
     * Record [closePrice] for a PRIVATE market asset on [date], creating or revaluing that
     * day's row. Any other asset, or no price, leaves storage alone and returns the stored row.
     */
    @Transactional
    fun record(
        assetId: String,
        date: LocalDate,
        closePrice: BigDecimal
    ): Optional<MarketData> {
        val asset = assetFinder.find(assetId)
        val existing =
            marketDataRepo.findByAssetIdAndPriceDate(
                asset.id,
                date
            )

        // For private market assets with a provided price, allow to create or update
        if (asset.market.code == PrivateMarketDataProvider.ID && closePrice != BigDecimal.ZERO) {
            return handlePrivateMarketPrice(asset, closePrice, date, existing)
        }

        return existing
    }

    /**
     * User-entered revaluation of a PRIVATE market asset (real estate, art, ...).
     *
     * Emits a cache-invalidation event so svc-position drops its cached
     * `performance_snapshot`; without it a re-valued private asset kept being charted
     * at its previous close.
     *
     * The event sweeps forward from [date] rather than invalidating that day alone.
     * A private asset has no daily feed - [PrivateMarketDataProvider] carries the
     * nearest price on-or-before a date forward - so correcting a back-dated price
     * changes every valuation after it, not just its own day.
     */
    private fun handlePrivateMarketPrice(
        asset: Asset,
        closePrice: BigDecimal,
        date: LocalDate,
        existing: Optional<MarketData>
    ): Optional<MarketData> {
        val marketData =
            if (existing.isPresent) {
                // Update existing price
                val md = existing.get()
                md.close = closePrice
                md
            } else {
                // Create new price
                MarketData(
                    asset = asset,
                    close = closePrice,
                    priceDate = date,
                    source = "USER"
                )
            }
        val saved = marketDataRepo.save(marketData)
        cacheInvalidationProducer?.sendPriceHistoryEvent(asset.id, date)
        return Optional.of(saved)
    }
}