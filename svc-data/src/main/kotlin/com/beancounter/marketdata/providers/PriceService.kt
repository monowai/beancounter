package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.MarketData.Companion.isDividend
import com.beancounter.common.model.MarketData.Companion.isSplit
import com.beancounter.common.utils.CashUtils
import com.beancounter.marketdata.event.EventProducer
import com.beancounter.marketdata.providers.custom.OffMarketDataProvider
import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

/**
 * Persist prices obtained from providers and detect if Corporate Events need to be dispatched.
 */
@Service
@Transactional
class PriceService internal constructor(
    private val marketDataRepo: MarketDataRepo,
    private val cashUtils: CashUtils,
) {
    private var eventProducer: EventProducer? = null

    @Autowired(required = false)
    fun setEventWriter(eventProducer: EventProducer?) {
        this.eventProducer = eventProducer
    }

    fun getMarketData(
        asset: Asset,
        date: LocalDate,
        closePrice: BigDecimal = BigDecimal.ZERO,
    ): Optional<MarketData> {
        val response = marketDataRepo.findByAssetIdAndPriceDate(asset.id, date)
        if (response.isPresent) return response
        return handleOffMarketPrice(asset, closePrice, date, response)
    }

    private fun handleOffMarketPrice(
        asset: Asset,
        closePrice: BigDecimal,
        date: LocalDate,
        response: Optional<MarketData>,
    ): Optional<MarketData> {
        if (asset.market.code == OffMarketDataProvider.ID && closePrice != BigDecimal.ZERO) {
            val price =
                marketDataRepo.save(
                    MarketData(
                        asset = asset,
                        close = closePrice,
                        priceDate = date,
                    ),
                )
            return Optional.of(price)
        }
        return response
    }

    @Async("priceExecutor")
    fun write(priceResponse: PriceResponse): Future<Iterable<MarketData>?> {
        return CompletableFuture.completedFuture(handle(priceResponse))
    }

    /**
     * Persistence and distribution of MarketData objects.
     */
    fun handle(priceResponse: PriceResponse): Iterable<MarketData>? {
        val createSet: MutableCollection<MarketData> = ArrayList()
        for (marketData in priceResponse.data) {
            if (!cashUtils.isCash(marketData.asset)) {
                val existing = getMarketData(marketData.asset, marketData.priceDate!!)
                if (existing.isEmpty) {
                    // Create
                    createSet.add(marketData)
                }
                if (eventProducer != null && isCorporateEvent(marketData)) {
                    eventProducer!!.write(marketData)
                }
            }
        }
        return if (createSet.isEmpty()) {
            createSet
        } else {
            marketDataRepo.saveAll(createSet)
        }
    }

    private fun isCorporateEvent(marketData: MarketData): Boolean {
        return marketData.asset.isKnown && (isDividend(marketData) || isSplit(marketData))
    }

    fun purge() {
        marketDataRepo.deleteAll()
    }

    fun purge(marketData: MarketData) {
        marketDataRepo.deleteById(marketData.id)
    }
}
