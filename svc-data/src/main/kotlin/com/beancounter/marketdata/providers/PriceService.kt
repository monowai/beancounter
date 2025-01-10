package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.MarketData.Companion.isDividend
import com.beancounter.common.model.MarketData.Companion.isSplit
import com.beancounter.common.utils.CashUtils
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.event.EventProducer
import com.beancounter.marketdata.providers.custom.OffMarketDataProvider
import jakarta.transaction.Transactional
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
    private val assetService: AssetService
) {
    private var eventProducer: EventProducer? = null

    @Autowired(required = false)
    fun setEventWriter(eventProducer: EventProducer?) {
        this.eventProducer = eventProducer
    }

    @Transactional
    fun getMarketData(
        assetId: String,
        date: LocalDate,
        closePrice: BigDecimal = BigDecimal.ZERO
    ): Optional<MarketData> {
        val asset = assetService.find(assetId)
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
                .filter {
                    getMarketData(
                        it.asset.id,
                        it.priceDate
                    ).isEmpty
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

    @Transactional
    fun purge() {
        marketDataRepo.deleteAll()
    }

    fun purge(marketData: MarketData) {
        marketDataRepo.deleteById(marketData.id)
    }

    // In PriceService.kt
    fun getMarketData(
        assets: Collection<Asset>,
        date: LocalDate
    ): List<MarketData> =
        marketDataRepo.findByAssetInAndPriceDate(
            assets,
            date
        )
}