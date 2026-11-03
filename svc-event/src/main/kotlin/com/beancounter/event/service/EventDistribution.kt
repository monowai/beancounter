package com.beancounter.event.service

import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.input.TrustedEventInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.model.TrnType
import com.beancounter.event.integration.EventPublisher
import com.beancounter.event.metrics.EventMetrics
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * Distributes a corporate event to the portfolios that held the asset on its record date,
 * publishing one proposed transaction per holder.
 */
@Service
@Transactional
class EventDistribution(
    private val positionService: PositionService,
    private val eventStore: CorporateEventStore,
    private val eventMetrics: EventMetrics
) {
    private lateinit var eventPublisher: EventPublisher

    private val log = LoggerFactory.getLogger(this::class.java)

    @Autowired(required = false)
    fun setEventPublisher(eventPublisher: EventPublisher) {
        this.eventPublisher = eventPublisher
    }

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
                        "Processed event: ${event.id}, asset: ${event.assetId}, " +
                            "portfolio: ${trnEvent.portfolio.code}, tradeDate: ${trnEvent.trnInput.tradeDate}"
                    )
                    eventPublisher.send(trnEvent)
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

        return results
    }
}