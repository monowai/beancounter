package com.beancounter.event.service

import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.TrustedEventInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.event.contract.CorporateEventResponse
import com.beancounter.event.contract.CorporateEventResponses
import com.beancounter.event.integration.EventPublisher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Corporate Action Event Processing Service. Stores and emits events.
 */
@Service
class EventService(
    private val positionService: PositionService,
    private val eventRepository: EventRepository,
    private val keyGenUtils: KeyGenUtils,
) {
    private lateinit var eventPublisher: EventPublisher

    private val log = LoggerFactory.getLogger(this::class.java)

    @Autowired(required = false)
    fun setEventPublisher(eventPublisher: EventPublisher) {
        this.eventPublisher = eventPublisher
    }

    fun process(eventRequest: TrustedEventInput): Collection<TrustedTrnEvent> =
        processEvent(
            save(eventRequest.data),
        )

    fun processEvent(event: CorporateEvent): Collection<TrustedTrnEvent> {
        val response =
            positionService.findWhereHeld(
                event.assetId,
                event.recordDate,
            )

        return response.data.mapNotNull { portfolio ->
            val trnEvent =
                positionService.process(
                    portfolio,
                    event,
                )
            // Skip forward dated transactions
            if (trnEvent.trnInput.trnType == TrnType.IGNORE) {
                null
            } else {
                log.info(
                    "Processed event: ${event.id}, asset: ${event.assetId}, " +
                        "portfolio: ${trnEvent.portfolio.code}, tradeDate: ${trnEvent.trnInput.tradeDate}",
                )
                eventPublisher.send(trnEvent)
                trnEvent
            }
        }
    }

    fun save(event: CorporateEvent): CorporateEvent {
        val existing =
            eventRepository.findByAssetIdAndRecordDate(
                event.assetId,
                event.recordDate,
            )

        // Check if an existing event is present and return it if so
        if (existing.isPresent) {
            return existing.get()
        }
        // Create a new event if no existing one is found
        val newEvent =
            CorporateEvent(
                id = keyGenUtils.id,
                trnType = event.trnType,
                source = event.source,
                assetId = event.assetId,
                recordDate = event.recordDate,
                rate = event.rate,
                split = event.split,
                payDate = event.payDate,
            )

        // Save the new event and log the details
        return eventRepository.save(newEvent).also { savedEvent ->
            log.trace(
                "Recorded type: {}, id: {}, assetId: {}, payDate: {}",
                savedEvent.trnType,
                savedEvent.id,
                savedEvent.assetId,
                savedEvent.payDate,
            )
        }
    }

    operator fun get(id: String): CorporateEventResponse =
        eventRepository
            .findById(id)
            .map { CorporateEventResponse(it) }
            .orElseThrow { BusinessException("Not found $id") }

    fun getAssetEvents(assetId: String): CorporateEventResponses {
        val events = forAsset(assetId)
        return CorporateEventResponses(events)
    }

    fun forAsset(assetId: String): Collection<CorporateEvent> = eventRepository.findByAssetIdOrderByPayDateDesc(assetId)

    fun findInRange(
        start: LocalDate,
        end: LocalDate,
    ): Collection<CorporateEvent> =
        eventRepository.findByDateRange(
            start,
            end,
        )

    fun getScheduledEvents(start: LocalDate): CorporateEventResponses =
        CorporateEventResponses(eventRepository.findByStartDate(start))

    fun find(
        assetIds: Collection<String>,
        recordDate: LocalDate,
    ): Collection<CorporateEvent> =
        eventRepository.findByAssetsAndRecordDate(
            assetIds,
            recordDate,
        )
}
