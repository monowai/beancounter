package com.beancounter.event.service

import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.event.utils.TestHelpers
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.Optional

/**
 * CorporateEventStore: idempotent save on asset and record date, and the event queries.
 */
@ExtendWith(MockitoExtension::class)
class CorporateEventStoreTest {
    @Mock
    private lateinit var eventRepository: EventRepository

    @Mock
    private lateinit var keyGenUtils: KeyGenUtils

    private lateinit var eventStore: CorporateEventStore

    private lateinit var testCorporateEvent: CorporateEvent

    @BeforeEach
    fun setUp() {
        eventStore = CorporateEventStore(eventRepository, keyGenUtils)
        testCorporateEvent = TestHelpers.createTestCorporateEvent()
    }

    @Test
    fun `should return existing event when event already exists for asset and record date`() {
        // Given
        val existingEvent = testCorporateEvent.copy(id = "existing-event-id")
        whenever(eventRepository.findByAssetIdAndRecordDate(any(), any())).thenReturn(Optional.of(existingEvent))

        // When
        val result = eventStore.save(testCorporateEvent)

        // Then
        assertThat(result).isEqualTo(existingEvent)
        verify(eventRepository).findByAssetIdAndRecordDate(testCorporateEvent.assetId, testCorporateEvent.recordDate)
    }

    @Test
    fun `should create new event when no existing event found for asset and record date`() {
        // Given
        val newEventId = "new-event-id"
        val newEvent = testCorporateEvent.copy(id = newEventId)

        whenever(keyGenUtils.id).thenReturn(newEventId)
        whenever(eventRepository.findByAssetIdAndRecordDate(any(), any())).thenReturn(Optional.empty())
        whenever(eventRepository.save(any<CorporateEvent>())).thenReturn(newEvent)

        // When
        val result = eventStore.save(testCorporateEvent)

        // Then
        assertThat(result).isEqualTo(newEvent)
        assertThat(result.id).isEqualTo(newEventId)
        verify(eventRepository).save(any())
    }

    @Test
    fun `should return corporate event response when event exists by ID`() {
        // Given
        val eventId = "test-event-id"
        val event = testCorporateEvent.copy(id = eventId)
        whenever(eventRepository.findById(eventId)).thenReturn(Optional.of(event))

        // When
        val result = eventStore[eventId]

        // Then
        assertThat(result).isNotNull()
        assertThat(result.data).isEqualTo(event)
        verify(eventRepository).findById(eventId)
    }

    @Test
    fun `should throw NotFoundException when event not found by ID`() {
        // Given
        val eventId = "non-existent-event"
        whenever(eventRepository.findById(eventId)).thenReturn(Optional.empty())

        // When & Then
        assertThatThrownBy { eventStore[eventId] }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("Event not found: $eventId")
    }

    @Test
    fun `should return corporate event responses when getting asset events`() {
        // Given
        val assetId = "AAPL"
        val events = listOf(testCorporateEvent)
        whenever(eventRepository.findByAssetIdOrderByPayDateDesc(assetId)).thenReturn(events)

        // When
        val result = eventStore.getAssetEvents(assetId)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.data).hasSize(1)
        assertThat(result.data.first()).isEqualTo(testCorporateEvent)
        verify(eventRepository).findByAssetIdOrderByPayDateDesc(assetId)
    }

    @Test
    fun `should return events for asset when querying by asset ID`() {
        // Given
        val assetId = "AAPL"
        val events = listOf(testCorporateEvent)
        whenever(eventRepository.findByAssetIdOrderByPayDateDesc(assetId)).thenReturn(events)

        // When
        val result = eventStore.forAsset(assetId)

        // Then
        assertThat(result).hasSize(1)
        assertThat(result.first()).isEqualTo(testCorporateEvent)
        verify(eventRepository).findByAssetIdOrderByPayDateDesc(assetId)
    }

    @Test
    fun `should return events in date range when querying by start and end dates`() {
        // Given
        val startDate = LocalDate.parse("2024-01-01")
        val endDate = LocalDate.parse("2024-01-31")
        val events = listOf(testCorporateEvent)
        whenever(eventRepository.findByDateRange(startDate, endDate)).thenReturn(events)

        // When
        val result = eventStore.findInRange(startDate, endDate)

        // Then
        assertThat(result).hasSize(1)
        assertThat(result.first()).isEqualTo(testCorporateEvent)
        verify(eventRepository).findByDateRange(startDate, endDate)
    }

    @Test
    fun `should return scheduled events when querying by start date`() {
        // Given
        val startDate = LocalDate.parse("2024-01-01")
        val events = listOf(testCorporateEvent)
        whenever(eventRepository.findByStartDate(startDate)).thenReturn(events)

        // When
        val result = eventStore.getScheduledEvents(startDate)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.data).hasSize(1)
        assertThat(result.data.first()).isEqualTo(testCorporateEvent)
        verify(eventRepository).findByStartDate(startDate)
    }

    @Test
    fun `should return events for assets and record date when querying multiple assets`() {
        // Given
        val assetIds = listOf("AAPL", "MSFT")
        val recordDate = LocalDate.parse("2024-01-15")
        val events = listOf(testCorporateEvent)
        whenever(eventRepository.findByAssetsAndRecordDate(assetIds, recordDate)).thenReturn(events)

        // When
        val result = eventStore.find(assetIds, recordDate)

        // Then
        assertThat(result).hasSize(1)
        assertThat(result.first()).isEqualTo(testCorporateEvent)
        verify(eventRepository).findByAssetsAndRecordDate(assetIds, recordDate)
    }

    @Test
    fun `should delete all events for an asset`() {
        // Given
        val assetId = "GNE-NZX"
        val events =
            listOf(
                testCorporateEvent.copy(id = "event-1", assetId = assetId),
                testCorporateEvent.copy(id = "event-2", assetId = assetId)
            )
        whenever(eventRepository.findByAssetIdOrderByPayDateDesc(assetId)).thenReturn(events)

        // When
        val count = eventStore.deleteForAsset(assetId)

        // Then
        assertThat(count).isEqualTo(2)
        verify(eventRepository).findByAssetIdOrderByPayDateDesc(assetId)
        verify(eventRepository).deleteAll(events)
    }

    @Test
    fun `should return zero when deleting events for asset with no events`() {
        // Given
        val assetId = "NO-EVENTS"
        whenever(eventRepository.findByAssetIdOrderByPayDateDesc(assetId)).thenReturn(emptyList())

        // When
        val count = eventStore.deleteForAsset(assetId)

        // Then
        assertThat(count).isEqualTo(0)
    }
}