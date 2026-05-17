package com.beancounter.marketdata.cache

import com.beancounter.common.contracts.CacheChangeType
import com.beancounter.common.contracts.CacheInvalidationEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.messaging.MessagingException
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class CacheInvalidationProducer(
    private val streamBridge: StreamBridge
) {
    @Value("\${stream.enabled:true}")
    var streamEnabled: Boolean = true

    fun sendTransactionEvent(
        portfolioId: String,
        fromDate: LocalDate
    ) {
        send(
            CacheInvalidationEvent(
                changeType = CacheChangeType.TRANSACTION,
                portfolioId = portfolioId,
                fromDate = fromDate
            )
        )
    }

    fun sendPriceEvent(date: LocalDate) {
        send(
            CacheInvalidationEvent(
                changeType = CacheChangeType.PRICE,
                fromDate = date
            )
        )
    }

    fun sendFxEvent(date: LocalDate) {
        send(
            CacheInvalidationEvent(
                changeType = CacheChangeType.FX,
                fromDate = date
            )
        )
    }

    /**
     * Emitted after a deep-history backfill completes for [assetId]. svc-position
     * uses this to drop cached performance snapshots from [fromDate] onwards so
     * the next read recomputes against the widened price series.
     */
    fun sendPriceHistoryEvent(
        assetId: String,
        fromDate: LocalDate
    ) {
        send(
            CacheInvalidationEvent(
                changeType = CacheChangeType.PRICE_HISTORY,
                assetId = assetId,
                fromDate = fromDate
            )
        )
    }

    private fun send(event: CacheInvalidationEvent) {
        if (!streamEnabled) return
        try {
            streamBridge.send(BINDING, event)
            log.trace("Cache invalidation sent: {}", event)
        } catch (e: MessagingException) {
            log.error("Failed to send cache invalidation event: {}", e.message)
        }
    }

    companion object {
        private const val BINDING = "cacheInvalidation-out-0"
        private val log = LoggerFactory.getLogger(CacheInvalidationProducer::class.java)
    }
}