package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.event.EventWriter
import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.AsyncResult
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.Optional
import java.util.concurrent.Future

/**
 * Persist prices obtained from providers and detect if Corporate Events need to be dispatched.
 */
@Service
@Transactional
class PriceService internal constructor(
    private val marketDataRepo: MarketDataRepo,
    private val keyGenUtils: KeyGenUtils
) {
    private lateinit var eventWriter: EventWriter

    @Autowired
    fun setEventWriter(eventWriter: EventWriter) {
        this.eventWriter = eventWriter
    }

    fun getMarketData(asset: Asset, date: LocalDate?): Optional<MarketData> {
        return marketDataRepo.findByAssetIdAndPriceDate(asset.id, date)
    }

    @Async("priceExecutor")
    fun write(priceResponse: PriceResponse): Future<Iterable<MarketData>?> {
        return AsyncResult(handle(priceResponse))
    }

    /**
     * Persistence and distribution of MarketData objects.
     */
    fun handle(priceResponse: PriceResponse): Iterable<MarketData>? {
        val createSet: MutableCollection<MarketData> = ArrayList()
        for (marketData in priceResponse.data) {
            if (!marketData.asset.assetCategory.isCash()) {
                val existing = getMarketData(marketData.asset, marketData.priceDate)
                if (existing.isEmpty) {
                    // Create
                    marketData.id = keyGenUtils.id
                    createSet.add(marketData)
                }
                if (writeEvent(marketData)) {
                    eventWriter.write(marketData)
                }
            }
        }
        return if (createSet.isEmpty()) {
            createSet
        } else {
            marketDataRepo.saveAll(createSet)
        }
    }

    private fun writeEvent(marketData: MarketData): Boolean {
        return marketData.asset.isKnown && (marketData.isDividend() || marketData.isSplit())
    }

    fun purge() {
        marketDataRepo.deleteAll()
    }
}
