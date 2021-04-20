package com.beancounter.event.service

import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.TrustedEventInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.event.contract.CorporateEventResponse
import com.beancounter.event.contract.CorporateEventsResponse
import com.beancounter.event.integration.EventPublisher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.ArrayList

/**
 * Corporate Action Event Processing Service. Stores and emits events.
 */
@Service
class EventService(
    private val positionService: PositionService,
    private val eventRepository: EventRepository,
    private val keyGenUtils: KeyGenUtils,
) {
    private var eventPublisher: EventPublisher? = null

    @Autowired(required = false)
    fun setEventPublisher(eventPublisher: EventPublisher?) {
        this.eventPublisher = eventPublisher
    }

    fun processMessage(eventRequest: TrustedEventInput): Collection<TrustedTrnEvent> {
        return processMessage(
            save(eventRequest.data)
        )
    }

    fun processMessage(event: CorporateEvent): Collection<TrustedTrnEvent> {
        val results: MutableCollection<TrustedTrnEvent> = ArrayList()
        val response = positionService.findWhereHeld(
            event.assetId,
            event.recordDate
        )
        for (portfolio in response.data) {
            val trnEvent = positionService.process(portfolio, event)
            // Don't create forward dated transactions
            if (trnEvent != null) {
                trnEvent.trnInput
                if (eventPublisher != null) {
                    eventPublisher!!.send(trnEvent)
                }
                results.add(trnEvent)
            }
        }
        return results
    }

    fun save(event: CorporateEvent): CorporateEvent {
        val existing = eventRepository.findByAssetIdAndRecordDate(
            event.assetId,
            event.recordDate
        )
        if (existing.isPresent) {
            return existing.get()
        }
        val save = CorporateEvent(
            keyGenUtils.id,
            event.trnType,
            event.source,
            event.assetId,
            event.recordDate,
            event.rate,
            event.split,
            event.payDate
        )
        return eventRepository.save(save)
    }

    operator fun get(id: String): CorporateEventResponse {
        val result = eventRepository.findById(id)
        return result
            .map { data: CorporateEvent? -> CorporateEventResponse(data!!) }
            .orElseThrow { BusinessException("Not found $id") }
    }

    fun getAssetEvents(assetId: String): CorporateEventsResponse {
        val events = eventRepository.findByAssetId(assetId)
        if (events.isNullOrEmpty()) {
            return CorporateEventsResponse()
        }
        return CorporateEventsResponse(events)
    }

    fun forAsset(assetId: String): Collection<CorporateEvent> {
        return eventRepository.findByAssetId(assetId)
    }

    fun backFillEvents(portfolioId: String, valuationDate: String) {
        positionService.backFillEvents(portfolioId, valuationDate)
    }

    fun findInRange(start: LocalDate, end: LocalDate): Collection<CorporateEvent> {
        return eventRepository.findByDateRange(start, end)
    }

    fun getScheduledEvents(start: LocalDate): CorporateEventsResponse {
        val events = eventRepository.findByStartDate(start)
        return CorporateEventsResponse(events)
    }
}
