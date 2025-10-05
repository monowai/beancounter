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
import com.beancounter.marketdata.providers.custom.OffMarketDataProvider
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
        val response =
            marketDataRepo.findByAssetIdAndPriceDate(
                asset.id,
                date
            )
        if (response.isPresent) return response
        return handleOffMarketPrice(
            asset,
            closePrice,
            date,
            response
        )
    }

    private fun handleOffMarketPrice(
        asset: Asset,
        closePrice: BigDecimal,
        date: LocalDate,
        response: Optional<MarketData>
    ): Optional<MarketData> {
        if (asset.market.code == OffMarketDataProvider.ID && closePrice != BigDecimal.ZERO) {
            val price =
                marketDataRepo.save(
                    MarketData(
                        asset = asset,
                        close = closePrice,
                        priceDate = date
                    )
                )
            return Optional.of(price)
        }
        return response
    }

    /**
     * Persistence and distribution of MarketData objects.
     */
    @Transactional
    fun handle(priceResponse: PriceResponse): Iterable<MarketData> {
        val createSet =
            priceResponse.data
                .filter { !cashUtils.isCash(it.asset) && it.close != BigDecimal.ZERO }
                .filter { marketData ->
                    // Skip if already exists (idempotent operation)
                    marketDataRepo.countByAssetIdAndPriceDate(
                        marketData.asset.id,
                        marketData.priceDate
                    ) == 0L
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

    private fun isCorporateEvent(marketData: MarketData): Boolean =
        marketData.asset.isKnown && (isDividend(marketData) || isSplit(marketData))

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

    // In PriceService.kt
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
     * SAFEGUARD: Count market data records for an asset on a specific date
     */
    @Transactional
    fun getMarketDataCount(
        assetId: String,
        date: LocalDate
    ): Long = marketDataRepo.countByAssetIdAndPriceDate(assetId, date)
}