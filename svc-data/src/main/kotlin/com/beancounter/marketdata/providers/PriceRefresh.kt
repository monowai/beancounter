package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.DateUtils.Companion.TODAY
import com.beancounter.marketdata.assets.AssetFinder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Update prices with smart retry logic.
 *
 * Only fetches prices for assets that don't have today's price yet,
 * making subsequent schedule runs efficient (only retrying failures).
 */
@Service
class PriceRefresh(
    private val assetFinder: AssetFinder,
    private val marketDataService: MarketDataService,
    private val priceService: PriceService,
    private val dateUtils: DateUtils,
    @Value("\${price.refresh.benchmarks:}") private val benchmarkSpecs: List<String> = emptyList()
) {
    private val log = LoggerFactory.getLogger(PriceRefresh::class.java)

    /**
     * Update prices for all assets that don't have today's price.
     *
     * This method is idempotent - running multiple times will only fetch
     * prices for assets that failed in previous runs.
     */
    fun updatePrices(): Int {
        val priceDate = dateUtils.getFormattedDate()
        log.info(
            "Scheduled price update starting for {} @ {} - {}",
            priceDate,
            LocalDateTime.now(dateUtils.zoneId),
            dateUtils.zoneId.id
        )

        val totalAssets = AtomicInteger()
        val skipped = AtomicInteger()
        val fetched = AtomicInteger()
        val failed = AtomicInteger()

        // Assets are collected upfront so no long-running DB transaction
        // wraps the price-fetch loop.  Without this, a single fetch failure
        // could mark the transaction rollback-only and discard ALL saved prices.
        // Index benchmarks (INDEX market) are unioned in — they have no
        // holdings but dashboards expect them refreshed daily. Configured
        // benchmarks join them: chart overlays divide by tickers (SPY, RSP)
        // that live on a regular market and that nobody need own.
        val held = assetFinder.findHeldAssetsForPricing()
        val indices = assetFinder.findActiveIndexAssets()
        val assets =
            (held + indices + benchmarkAssets())
                .distinctBy { it.id }

        for (asset in assets) {
            totalAssets.getAndIncrement()

            // Skip if we already have today's price
            if (hasTodaysPrice(asset)) {
                skipped.getAndIncrement()
                continue
            }

            // Fetch price for this asset
            try {
                val priceRequest = PriceRequest.of(asset, TODAY)
                val response = marketDataService.getPriceResponse(priceRequest)

                if (response.data.isNotEmpty() &&
                    response.data
                        .first()
                        .close
                        .signum() > 0
                ) {
                    fetched.getAndIncrement()
                } else {
                    failed.getAndIncrement()
                    log.debug("No valid price returned for {}", asset.code)
                }
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception
            ) {
                // Continue processing other assets even if one fails
                failed.getAndIncrement()
                log.warn("Failed to fetch price for {}: {}", asset.code, e.message)
            }
        }

        log.info(
            "Price update completed @ {} - total: {}, skipped (already priced): {}, fetched: {}, failed: {}",
            LocalDateTime.now(dateUtils.zoneId),
            totalAssets.get(),
            skipped.get(),
            fetched.get(),
            failed.get()
        )

        return fetched.get()
    }

    /**
     * Resolve `price.refresh.benchmarks` — `MARKET:CODE` entries, e.g. `US:SPY`
     * — to assets. A ticker nobody holds is invisible to
     * [AssetFinder.findHeldAssetsForPricing], so without this its cached tail
     * stops on whatever day it was last backfilled and every chart that divides
     * by it draws short.
     *
     * A spec that doesn't resolve is logged and dropped, not thrown: a typo in
     * config must not stop the held assets in the same run from being priced.
     */
    private fun benchmarkAssets(): List<Asset> =
        // An unset property binds as a single empty string, not an empty list —
        // dropping blanks keeps the default config out of the warn path below.
        benchmarkSpecs.filter { it.isNotBlank() }.mapNotNull { spec ->
            val parts = spec.trim().split(":")
            if (parts.size != 2 || parts.any { it.isBlank() }) {
                log.warn("Ignoring malformed price.refresh.benchmarks entry '{}' — expected MARKET:CODE", spec)
                return@mapNotNull null
            }
            val (market, code) = parts.map { it.trim().uppercase(Locale.getDefault()) }
            val asset = assetFinder.findLocally(AssetInput(market, code))
            if (asset == null) {
                log.warn("Benchmark {}:{} is not a known asset — skipping", market, code)
            }
            asset
        }

    /**
     * Check if asset already has a valid price for today.
     */
    private fun hasTodaysPrice(asset: Asset): Boolean {
        val priceDate = dateUtils.getFormattedDate()
        return priceService.getMarketDataCount(asset.id, priceDate) > 0
    }

    fun refreshPrice(
        assetId: String,
        date: String = dateUtils.getFormattedDate().toString()
    ): PriceResponse {
        log.info(
            "Updating Prices {} for {}",
            LocalDateTime.now(dateUtils.zoneId),
            assetId
        )
        val asset = getAsset(assetId)
        val priceRequest =
            PriceRequest.of(
                asset,
                date
            )
        marketDataService.refresh(
            asset,
            date
        )
        val response = marketDataService.getPriceResponse(priceRequest)
        log.info(
            "Refreshed asset price for ${asset.name} "
        )
        return response
    }

    private fun getAsset(assetId: String): Asset {
        val asset = assetFinder.find(assetId)
        return asset
    }
}