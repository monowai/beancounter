package com.beancounter.event.distribution

import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.input.TrustedEventInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.model.TrnType
import com.beancounter.event.corporate.CorporateEventStore
import com.beancounter.event.metrics.EventMetrics
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Distributes a corporate event to the portfolios that held the asset on its record date,
 * publishing one proposed transaction per holder after the transaction commits.
 */
@Service
@Transactional
class EventDistribution(
    private val positionService: PositionService,
    private val eventStore: CorporateEventStore,
    private val eventMetrics: EventMetrics,
    private val eventPublisher: EventPublisher
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Suppress("TooGenericExceptionCaught") // Intentionally catching all exceptions for metrics
    fun process(eventRequest: TrustedEventInput): Collection<TrustedTrnEvent> {
        // Record event received
        val isDividend = eventRequest.data.trnType == TrnType.DIVI
        eventMetrics.recordEventReceived(isDividend)

        return try {
            eventMetrics.timeEventProcessing {
                processEvent(eventStore.save(eventRequest.data))
            }
        } catch (e: Exception) {
            eventMetrics.recordEventError(e.javaClass.simpleName)
            throw e
        }
    }

    fun processEvent(
        event: CorporateEvent,
        overridePayDate: String? = null
    ): Collection<TrustedTrnEvent> {
        val response =
            positionService.findWhereHeld(
                event.assetId,
                event.recordDate
            )

        val portfolioCount = response.data.size
        var ignoredCount = 0
        var transactionCount = 0

        val results =
            response.data.mapNotNull { portfolio ->
                val trnEvent =
                    positionService.process(
                        portfolio,
                        event,
                        overridePayDate
                    )
                // Skip ignored transactions (e.g., unsupported event types)
                if (trnEvent.trnInput.trnType == TrnType.IGNORE) {
                    ignoredCount++
                    null
                } else {
                    transactionCount++
                    log.info(
                        "Prepared trn event: ${event.id}, asset: ${event.assetId}, " +
                            "portfolio: ${trnEvent.portfolio.code}, tradeDate: ${trnEvent.trnInput.tradeDate}"
                    )
                    trnEvent
                }
            }

        // Record metrics
        if (transactionCount > 0) {
            eventMetrics.recordEventProcessed(portfolioCount, transactionCount)
        }
        if (ignoredCount > 0) {
            eventMetrics.recordEventIgnored("unsupported_event_type")
        }

        publishAfterCommit(results)
        return results
    }

    /**
     * Sends once the surrounding transaction commits, so a rollback part way through the
     * holders leaves nothing on the wire. Without a transaction, sends at once.
     */
    private fun publishAfterCommit(trnEvents: Collection<TrustedTrnEvent>) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sendEach(trnEvents)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = sendEach(trnEvents)
            }
        )
    }

    /** One failed send is logged and counted; the other holders' events still go out. */
    private fun sendEach(trnEvents: Collection<TrustedTrnEvent>) {
        trnEvents.forEach { trnEvent ->
            runCatching { eventPublisher.send(trnEvent) }
                .onFailure {
                    log.error("Send failed, portfolio: ${trnEvent.portfolio.id}", it)
                    eventMetrics.recordEventError("send_failed")
                }
        }
    }
}