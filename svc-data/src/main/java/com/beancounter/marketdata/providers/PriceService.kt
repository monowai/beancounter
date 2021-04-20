package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.event.EventWriter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.AsyncResult
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.Optional
import java.util.concurrent.Future
import javax.transaction.Transactional

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

    fun getMarketData(assetId: String, date: LocalDate?): Optional<MarketData> {
        return marketDataRepo.findByAssetIdAndPriceDate(assetId, date)
    }

    @Async("priceExecutor")
    fun write(priceResponse: PriceResponse): Future<Iterable<MarketData>?> {
        return AsyncResult(process(priceResponse))
    }

    fun process(priceResponse: PriceResponse): Iterable<MarketData>? {
        val createSet: MutableCollection<MarketData> = ArrayList()
        for (marketData in priceResponse.data) {
            if (marketData.asset.isKnown) {
                val existing = marketDataRepo.findByAssetIdAndPriceDate(
                    marketData.asset.id,
                    marketData.priceDate
                )
                if (existing.isEmpty) {
                    // Create
                    marketData.id = keyGenUtils.id
                    createSet.add(marketData)
                }
                eventWriter.write(marketData)
            }
        }
        return if (createSet.isEmpty()) {
            createSet
        } else marketDataRepo.saveAll(createSet)
    }

    fun purge() {
        marketDataRepo.deleteAll()
    }
}
