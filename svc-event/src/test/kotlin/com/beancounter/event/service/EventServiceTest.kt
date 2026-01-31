package com.beancounter.event.service

import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.input.TrustedEventInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.event.integration.EventPublisher
import com.beancounter.event.metrics.EventMetrics
import com.beancounter.event.utils.TestHelpers
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.Optional

/**
 * Test suite for EventService to ensure proper corporate event processing functionality.
 *
 * This class tests:
 * - Corporate event processing and saving
 * - Event retrieval by ID and asset
 * - Event scheduling and range queries
 * - Integration with position service and event publisher
 * - Error handling for missing events
 *
 * Tests verify that the EventService correctly processes
 * corporate events and manages event lifecycle operations.
 */
@ExtendWith(MockitoExtension::class)
class EventServiceTest {
    @Mock
    private lateinit var positionService: PositionService

    @Mock
    private lateinit var eventRepository: EventRepository

    @Mock
    private lateinit var keyGenUtils: KeyGenUtils

    @Mock
    private lateinit var eventPublisher: EventPublisher

    @Mock
    private lateinit var eventMetrics: EventMetrics

    private lateinit var eventService: EventService

    private lateinit var testCorporateEvent: CorporateEvent
    private lateinit var testTrustedEventInput: TrustedEventInput
    private lateinit var testPortfolio: Portfolio
    private lateinit var testTrustedTrnEvent: TrustedTrnEvent

    @BeforeEach
    fun setUp() {
        // Stub timeEventProcessing to execute the lambda (lenient to avoid unnecessary stubbing errors)
        lenient().`when`(eventMetrics.timeEventProcessing<Collection<TrustedTrnEvent>>(any())).doAnswer { invocation ->
            val operation = invocation.getArgument<() -> Collection<TrustedTrnEvent>>(0)
            operation.invoke()
        }

        eventService = EventService(positionService, eventRepository, keyGenUtils, eventMetrics)
        eventService.setEventPublisher(eventPublisher)

        testCorporateEvent = TestHelpers.createTestCorporateEvent()
        testTrustedEventInput = TrustedEventInput(testCorporateEvent)
        testPortfolio = TestHelpers.createTestPortfolio()
        testTrustedTrnEvent = TestHelpers.createTestTrustedTrnEvent(testPortfolio)
    }

    @Test
    fun `should process event and return transaction events when valid event input is provided`() {
        // Given
        val eventId = "test-event-id"
        val savedEvent = testCorporateEvent.copy(id = eventId)
        val portfoliosResponse = PortfoliosResponse(listOf(testPortfolio))

        whenever(keyGenUtils.id).thenReturn(eventId)
        whenever(eventRepository.findByAssetIdAndRecordDate(any(), any())).thenReturn(Optional.empty())
        whenever(eventRepository.save(any())).thenReturn(savedEvent)
        whenever(positionService.findWhereHeld(any(), any())).thenReturn(portfoliosResponse)
        whenever(positionService.process(any(), any(), anyOrNull())).thenReturn(testTrustedTrnEvent)

        // When
        val result = eventService.process(testTrustedEventInput)

        // Then
        assertThat(result).hasSize(1)
        assertThat(result.first()).isEqualTo(testTrustedTrnEvent)
        verify(eventRepository).save(any())
        verify(positionService).findWhereHeld(testCorporateEvent.assetId, testCorporateEvent.recordDate)
        verify(eventPublisher).send(testTrustedTrnEvent)
    }

    @Test
    fun `should return existing event when event already exists for asset and record date`() {
        // Given
        val existingEvent = testCorporateEvent.copy(id = "existing-event-id")
        whenever(eventRepository.findByAssetIdAndRecordDate(any(), any())).thenReturn(Optional.of(existingEvent))

        // When
        val result = eventService.save(testCorporateEvent)

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
        whenever(eventRepository.save(any())).thenReturn(newEvent)

        // When
        val result = eventService.save(testCorporateEvent)

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
        val result = eventService[eventId]

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
        assertThatThrownBy { eventService[eventId] }
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
        val result = eventService.getAssetEvents(assetId)

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
        val result = eventService.forAsset(assetId)

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
        val result = eventService.findInRange(startDate, endDate)

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
        val result = eventService.getScheduledEvents(startDate)

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
        val result = eventService.find(assetIds, recordDate)

        // Then
        assertThat(result).hasSize(1)
        assertThat(result.first()).isEqualTo(testCorporateEvent)
        verify(eventRepository).findByAssetsAndRecordDate(assetIds, recordDate)
    }

    @Test
    fun `should skip forward dated transactions when processing event`() {
        // Given
        val eventId = "test-event-id"
        val savedEvent = testCorporateEvent.copy(id = eventId)
        val portfoliosResponse = PortfoliosResponse(listOf(testPortfolio))
        val ignoredTrnEvent =
            testTrustedTrnEvent.copy(
                trnInput = testTrustedTrnEvent.trnInput.copy(trnType = TrnType.IGNORE)
            )

        whenever(keyGenUtils.id).thenReturn(eventId)
        whenever(eventRepository.findByAssetIdAndRecordDate(any(), any())).thenReturn(Optional.empty())
        whenever(eventRepository.save(any())).thenReturn(savedEvent)
        whenever(positionService.findWhereHeld(any(), any())).thenReturn(portfoliosResponse)
        whenever(positionService.process(any(), any(), anyOrNull())).thenReturn(ignoredTrnEvent)

        // When
        val result = eventService.process(testTrustedEventInput)

        // Then
        assertThat(result).isEmpty()
        verify(eventRepository).save(any())
        verify(positionService).findWhereHeld(testCorporateEvent.assetId, testCorporateEvent.recordDate)
    }

    @Test
    fun `should pass overridePayDate to position service when processing event`() {
        // Given
        val eventId = "test-event-id"
        val savedEvent = testCorporateEvent.copy(id = eventId)
        val portfoliosResponse = PortfoliosResponse(listOf(testPortfolio))
        val overridePayDate = "2024-03-15"

        whenever(positionService.findWhereHeld(any(), any())).thenReturn(portfoliosResponse)
        whenever(positionService.process(any(), any(), eq(overridePayDate))).thenReturn(testTrustedTrnEvent)

        // When
        eventService.processEvent(savedEvent, overridePayDate)

        // Then
        verify(positionService).process(testPortfolio, savedEvent, overridePayDate)
    }

    @Test
    fun `should pass null overridePayDate when not provided`() {
        // Given
        val eventId = "test-event-id"
        val savedEvent = testCorporateEvent.copy(id = eventId)
        val portfoliosResponse = PortfoliosResponse(listOf(testPortfolio))

        whenever(positionService.findWhereHeld(any(), any())).thenReturn(portfoliosResponse)
        whenever(positionService.process(any(), any(), isNull())).thenReturn(testTrustedTrnEvent)

        // When
        eventService.processEvent(savedEvent)

        // Then
        verify(positionService).process(testPortfolio, savedEvent, null)
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
        val count = eventService.deleteForAsset(assetId)

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
        val count = eventService.deleteForAsset(assetId)

        // Then
        assertThat(count).isEqualTo(0)
    }

    @Test
    fun `should process event with override pay date for multiple portfolios`() {
        // Given
        val eventId = "multi-portfolio-event"
        val savedEvent = testCorporateEvent.copy(id = eventId)
        val secondPortfolio = testPortfolio.copy(id = "second-portfolio", code = "P2")
        val portfoliosResponse = PortfoliosResponse(listOf(testPortfolio, secondPortfolio))
        val overridePayDate = "2024-04-01"

        val secondTrustedTrnEvent = testTrustedTrnEvent.copy(portfolio = secondPortfolio)

        whenever(positionService.findWhereHeld(any(), any())).thenReturn(portfoliosResponse)
        whenever(positionService.process(testPortfolio, savedEvent, overridePayDate)).thenReturn(testTrustedTrnEvent)
        whenever(
            positionService.process(secondPortfolio, savedEvent, overridePayDate)
        ).thenReturn(secondTrustedTrnEvent)

        // When
        val result = eventService.processEvent(savedEvent, overridePayDate)

        // Then
        assertThat(result).hasSize(2)
        verify(positionService).process(testPortfolio, savedEvent, overridePayDate)
        verify(positionService).process(secondPortfolio, savedEvent, overridePayDate)
        verify(eventPublisher).send(testTrustedTrnEvent)
        verify(eventPublisher).send(secondTrustedTrnEvent)
    }
}