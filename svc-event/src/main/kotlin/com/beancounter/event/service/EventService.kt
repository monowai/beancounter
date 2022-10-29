package com.beancounter.event.service

import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.TrustedEventInput
import com.beancounter.common.input.TrustedTrnEvent
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
    private val keyGenUtils: KeyGenUtils
) {
    private var eventPublisher: EventPublisher? = null

    @Autowired(required = false)
    fun setEventPublisher(eventPublisher: EventPublisher) {
        this.eventPublisher = eventPublisher
    }

    fun process(eventRequest: TrustedEventInput): Collection<TrustedTrnEvent> {
        return processEvent(
            save(eventRequest.data)
        )
    }

    fun processEvent(event: CorporateEvent): Collection<TrustedTrnEvent> {
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
                    log.info(
                        "event: {}, asset: {} code: {}, tradeDate: {}",
                        event.id,
                        event.assetId,
                        trnEvent.portfolio.code,
                        trnEvent
                            .trnInput.tradeDate
                    )
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

        val corporateEvent = eventRepository.save(save)
        log.trace(
            "Recorded type: {}, id: {}, assetId: {}, payDate: {}",
            corporateEvent.trnType,
            corporateEvent.id,
            corporateEvent.assetId,
            corporateEvent.payDate
        )
        return corporateEvent
    }

    operator fun get(id: String): CorporateEventResponse {
        val result = eventRepository.findById(id)
        return result
            .map { data: CorporateEvent? -> CorporateEventResponse(data!!) }
            .orElseThrow { BusinessException("Not found $id") }
    }

    fun getAssetEvents(assetId: String): CorporateEventResponses {
        val events = eventRepository.findByAssetId(assetId)
        if (events.isEmpty()) {
            return CorporateEventResponses()
        }
        return CorporateEventResponses(events)
    }

    fun forAsset(assetId: String): Collection<CorporateEvent> {
        return eventRepository.findByAssetId(assetId)
    }

    fun findInRange(start: LocalDate, end: LocalDate): Collection<CorporateEvent> {
        return eventRepository.findByDateRange(start, end)
    }

    fun getScheduledEvents(start: LocalDate): CorporateEventResponses {
        val events = eventRepository.findByStartDate(start)
        return CorporateEventResponses(events)
    }

    fun find(assetIds: Collection<String>, recordDate: LocalDate): Collection<CorporateEvent> {
        return eventRepository.findByAssetsAndRecordDate(assetIds, recordDate)
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }
}
