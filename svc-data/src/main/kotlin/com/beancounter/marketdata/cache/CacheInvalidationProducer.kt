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

    /**
     * Publishes a cache invalidation event for foreign-exchange (FX) data starting at the given date.
     *
     * @param date The start date (`fromDate`) from which FX cache entries should be invalidated.
     */
    fun sendFxEvent(date: LocalDate) {
        send(
            CacheInvalidationEvent(
                changeType = CacheChangeType.FX,
                fromDate = date
            )
        )
    }

    /**
     * Publishes a `PRICE_HISTORY` cache invalidation event for the specified asset starting at the given date.
     *
     * @param assetId The identifier of the asset whose price history changed.
     * @param fromDate The earliest date (inclusive) from which cached price history should be considered invalid.
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

    /**
     * Publishes a CacheInvalidationEvent to the configured output binding when streaming is enabled.
     *
     * Does nothing if streaming is disabled. Any MessagingException raised while sending is caught and logged.
     *
     * @param event The cache invalidation event to publish (contains change type and related identifiers/dates).
     */
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