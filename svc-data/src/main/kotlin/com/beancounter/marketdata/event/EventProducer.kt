package com.beancounter.marketdata.event

import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.input.TrustedEventInput
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.MarketData.Companion.isDividend
import com.beancounter.common.model.MarketData.Companion.isSplit
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.metrics.TrnMetrics
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.stereotype.Service

/**
 * Corporate Action/Event publisher using Spring Cloud Stream.
 */
@Service
class EventProducer(
    private val streamBridge: StreamBridge,
    private val trnMetrics: TrnMetrics
) {
    @Value("\${stream.enabled:true}")
    var streamEnabled: Boolean = true

    fun write(marketData: MarketData) {
        if (!streamEnabled || !isValidEvent(marketData)) {
            return
        }

        // Record metrics for dividend/split detection
        if (isDividend(marketData)) {
            trnMetrics.recordDividendDetected()
        }
        if (isSplit(marketData)) {
            trnMetrics.recordSplitDetected()
        }

        val corporateEvent =
            CorporateEvent(
                id = null,
                trnType = TrnType.DIVI,
                source = marketData.source,
                recordDate = marketData.priceDate,
                assetId = marketData.asset.id,
                rate = marketData.dividend,
                split = marketData.split
            )
        log.trace(
            "Publishing corporate event: {}",
            marketData
        )
        streamBridge.send("corporateEvent-out-0", TrustedEventInput(corporateEvent))
        trnMetrics.recordCorporateEventPublished(corporateEvent.trnType.name)
    }

    private fun isValidEvent(marketData: MarketData?): Boolean =
        if (marketData == null) {
            false
        } else {
            isSplit(marketData) || isDividend(marketData)
        }

    companion object {
        private val log = LoggerFactory.getLogger(EventProducer::class.java)
    }
}