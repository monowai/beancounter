package com.beancounter.event.corporate

import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.event.behaviour.Event
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * The corporate events svc-event has recorded. Saving is idempotent on asset and record date.
 */
@Service
@Transactional
class CorporateEventStore(
    private val eventRepository: EventRepository,
    private val keyGenUtils: KeyGenUtils
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun save(event: CorporateEvent): CorporateEvent {
        val existing =
            eventRepository.findByAssetIdAndRecordDate(
                event.assetId,
                event.recordDate
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
                payDate = event.payDate
            )

        // Save the new event and log the details
        return eventRepository.save(newEvent).also { savedEvent ->
            log.trace(
                "Recorded type: {}, id: {}, assetId: {}, payDate: {}",
                savedEvent.trnType,
                savedEvent.id,
                savedEvent.assetId,
                savedEvent.payDate
            )
        }
    }

    operator fun get(id: String): CorporateEventResponse =
        eventRepository
            .findById(id)
            .map { CorporateEventResponse(it) }
            .orElseThrow { NotFoundException("Event not found: $id") }

    fun getAssetEvents(assetId: String): CorporateEventResponses {
        val events = forAsset(assetId)
        return CorporateEventResponses(events)
    }

    fun forAsset(assetId: String): Collection<CorporateEvent> = eventRepository.findByAssetIdOrderByPayDateDesc(assetId)

    fun findInRange(
        start: LocalDate,
        end: LocalDate
    ): Collection<CorporateEvent> =
        eventRepository.findByDateRange(
            start,
            end
        )

    fun getScheduledEvents(start: LocalDate): CorporateEventResponses =
        CorporateEventResponses(eventRepository.findByStartDate(start))

    fun deleteForAsset(assetId: String): Int {
        val events = forAsset(assetId)
        if (events.isEmpty()) {
            return 0
        }
        eventRepository.deleteAll(events)
        log.info("Deleted {} events for asset {}", events.size, assetId)
        return events.size
    }

    fun find(
        assetIds: Collection<String>,
        recordDate: LocalDate
    ): Collection<CorporateEvent> =
        eventRepository.findByAssetsAndRecordDate(
            assetIds,
            recordDate
        )
}