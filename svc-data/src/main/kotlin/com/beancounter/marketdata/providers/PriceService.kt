package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.MarketData.Companion.isDividend
import com.beancounter.common.model.MarketData.Companion.isSplit
import com.beancounter.common.utils.CashUtils
import com.beancounter.common.utils.TestEnvironmentUtils
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.event.EventProducer
import com.beancounter.marketdata.providers.custom.PrivateMarketDataProvider
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

/**
 * Persist prices obtained from providers and detect if Corporate Events need to be dispatched.
 */
@Service
class PriceService(
    private val marketDataRepo: MarketDataRepo,
    private val cashUtils: CashUtils,
    private val assetFinder: AssetFinder
) {
    private val log = LoggerFactory.getLogger(PriceService::class.java)
    private var eventProducer: EventProducer? = null

    @Autowired(required = false)
    fun setEventWriter(eventProducer: EventProducer?) {
        this.eventProducer = eventProducer
    }

    private fun getAsset(assetId: String): Asset {
        val asset = assetFinder.find(assetId)
        return asset
    }

    @Transactional
    fun getMarketData(
        assetId: String,
        date: LocalDate,
        closePrice: BigDecimal = BigDecimal.ZERO
    ): Optional<MarketData> {
        val asset = getAsset(assetId)
        val existing =
            marketDataRepo.findByAssetIdAndPriceDate(
                asset.id,
                date
            )

        // For private market assets with a provided price, allow create or update
        if (asset.market.code == PrivateMarketDataProvider.ID && closePrice != BigDecimal.ZERO) {
            return handlePrivateMarketPrice(asset, closePrice, date, existing)
        }

        return existing
    }

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
        return Optional.of(marketDataRepo.save(marketData))
    }

    /**
     * Persistence and distribution of MarketData objects.
     * If previousClose is not provided by the API, it is calculated from the previous day's close.
     *
     * IMPORTANT: Prices with close <= 0 are rejected as invalid data from the provider.
     * A zero or negative price indicates a provider issue and should never be stored.
     */
    @Transactional
    fun handle(priceResponse: PriceResponse): Iterable<MarketData> {
        val createSet =
            priceResponse.data
                .filter { !cashUtils.isCash(it.asset) && isValidPrice(it) }
                .filter { marketData ->
                    // Skip if already exists (idempotent operation)
                    marketDataRepo.countByAssetIdAndPriceDate(
                        marketData.asset.id,
                        marketData.priceDate
                    ) == 0L
                }.map { marketData ->
                    enrichWithPreviousClose(marketData)
                }

        priceResponse.data
            .filter { !cashUtils.isCash(it.asset) && isCorporateEvent(it) }
            .forEach { marketData ->
                eventProducer?.write(marketData)
            }

        return if (createSet.isEmpty()) {
            createSet
        } else {
            marketDataRepo.saveAll(createSet)
        }
    }

    /**
     * If previousClose is not provided by the API (zero), calculate it from the previous day's close.
     * Also calculates change and changePercent when previousClose is derived.
     */
    private fun enrichWithPreviousClose(marketData: MarketData): MarketData {
        if (marketData.previousClose.compareTo(BigDecimal.ZERO) != 0) {
            // API already provided previousClose, no enrichment needed
            return marketData
        }

        // Try to get previous day's close from database
        val previousDayData =
            marketDataRepo.findTop1ByAssetAndPriceDateLessThanOrderByPriceDateDesc(
                marketData.asset,
                marketData.priceDate
            )

        if (previousDayData.isPresent) {
            val previousClose = previousDayData.get().close
            marketData.previousClose = previousClose
            marketData.change = marketData.close.subtract(previousClose)
            if (previousClose.compareTo(BigDecimal.ZERO) != 0) {
                marketData.changePercent =
                    marketData.change.divide(previousClose, 6, java.math.RoundingMode.HALF_UP)
            }
            log.debug(
                "Calculated previousClose for {} on {}: {} -> change={}, changePercent={}",
                marketData.asset.code,
                marketData.priceDate,
                previousClose,
                marketData.change,
                marketData.changePercent
            )
        }

        return marketData
    }

    private fun isCorporateEvent(marketData: MarketData): Boolean =
        marketData.asset.isKnown && (isDividend(marketData) || isSplit(marketData))

    /**
     * Validates that a price is valid for storage.
     * A valid price must have a close price > 0.
     * Zero or negative prices indicate provider issues and are rejected.
     */
    private fun isValidPrice(marketData: MarketData): Boolean {
        val isValid = marketData.close.compareTo(BigDecimal.ZERO) > 0
        if (!isValid) {
            log.warn(
                "Rejecting invalid price for {} on {}: close={} (must be > 0)",
                marketData.asset.code,
                marketData.priceDate,
                marketData.close
            )
        }
        return isValid
    }

    /**
     * Delete all prices.  Supports testing ONLY!
     *
     * CRITICAL: This method should NEVER be called in production!
     * It will permanently delete ALL market data history.
     */
    @Transactional
    fun purge() {
        // SAFEGUARD: Only allow purge in test environments
        if (!TestEnvironmentUtils.isTestEnvironment()) {
            throw IllegalStateException(
                "CRITICAL ERROR: purge() method called in non-test environment! " +
                    "This would delete ALL market data history. " +
                    "Current profile: ${System.getProperty("spring.profiles.active")} " +
                    "Classpath: ${System.getProperty("java.class.path")}"
            )
        }

        log.warn("PURGE OPERATION: Deleting ALL market data - this should only happen in tests!")
        marketDataRepo.deleteAll()
    }

    /**
     * Delete specific market data record.  Supports testing ONLY!
     *
     * CRITICAL: This method should NEVER be called in production!
     * It will permanently delete market data history.
     */
    @Transactional
    fun purge(marketData: MarketData) {
        // SAFEGUARD: Only allow purge in test environments
        if (!TestEnvironmentUtils.isTestEnvironment()) {
            throw IllegalStateException(
                "CRITICAL ERROR: purge(MarketData) method called in non-test environment! " +
                    "This would delete market data history. " +
                    "Asset: ${marketData.asset.name}, Date: ${marketData.priceDate} " +
                    "Current profile: ${System.getProperty("spring.profiles.active")}"
            )
        }

        log.warn(
            "PURGE OPERATION: Deleting market data for ${marketData.asset.name} on ${marketData.priceDate} - this should only happen in tests!"
        )
        marketDataRepo.deleteById(marketData.id)
    }

    /**
     * Get market data for assets on a specific date (exact match only).
     */
    @Transactional
    fun getMarketData(
        assets: Collection<Asset>,
        date: LocalDate
    ): List<MarketData> =
        marketDataRepo.findByAssetInAndPriceDate(
            assets,
            date
        )

    /**
     * Get the most recent market data for an asset on or before the given date.
     * Used as a fallback when the market was closed on the requested date.
     */
    @Transactional
    fun getLatestMarketData(
        asset: Asset,
        date: LocalDate
    ): MarketData? =
        marketDataRepo
            .findTop1ByAssetAndPriceDateLessThanEqual(asset, date)
            .orElse(null)

    /**
     * SAFEGUARD: Count market data records for an asset on a specific date
     */
    @Transactional
    fun getMarketDataCount(
        assetId: String,
        date: LocalDate
    ): Long = marketDataRepo.countByAssetIdAndPriceDate(assetId, date)
}