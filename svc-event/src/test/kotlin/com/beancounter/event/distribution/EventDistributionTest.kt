package com.beancounter.event.distribution

import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.input.TrustedEventInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.event.corporate.CorporateEventStore
import com.beancounter.event.corporate.EventRepository
import com.beancounter.event.metrics.EventMetrics
import com.beancounter.event.utils.TestHelpers
import org.assertj.core.api.Assertions.assertThat
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

/**
 * EventDistribution: a corporate event becomes one published transaction per portfolio
 * that held the asset on the record date.
 */
@ExtendWith(MockitoExtension::class)
class EventDistributionTest {
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

    private lateinit var eventDistribution: EventDistribution

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

        eventDistribution =
            EventDistribution(
                positionService,
                CorporateEventStore(eventRepository, keyGenUtils),
                eventMetrics,
                eventPublisher
            )

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
        whenever(eventRepository.save(any<CorporateEvent>())).thenReturn(savedEvent)
        whenever(positionService.findWhereHeld(any(), any())).thenReturn(portfoliosResponse)
        whenever(positionService.process(any(), any(), anyOrNull())).thenReturn(testTrustedTrnEvent)

        // When
        val result = eventDistribution.process(testTrustedEventInput)

        // Then
        assertThat(result).hasSize(1)
        assertThat(result.first()).isEqualTo(testTrustedTrnEvent)
        verify(eventRepository).save(any())
        verify(positionService).findWhereHeld(testCorporateEvent.assetId, testCorporateEvent.recordDate)
        verify(eventPublisher).send(testTrustedTrnEvent)
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
        whenever(eventRepository.save(any<CorporateEvent>())).thenReturn(savedEvent)
        whenever(positionService.findWhereHeld(any(), any())).thenReturn(portfoliosResponse)
        whenever(positionService.process(any(), any(), anyOrNull())).thenReturn(ignoredTrnEvent)

        // When
        val result = eventDistribution.process(testTrustedEventInput)

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
        eventDistribution.processEvent(savedEvent, overridePayDate)

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
        eventDistribution.processEvent(savedEvent)

        // Then
        verify(positionService).process(testPortfolio, savedEvent, null)
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
        val result = eventDistribution.processEvent(savedEvent, overridePayDate)

        // Then
        assertThat(result).hasSize(2)
        verify(positionService).process(testPortfolio, savedEvent, overridePayDate)
        verify(positionService).process(secondPortfolio, savedEvent, overridePayDate)
        verify(eventPublisher).send(testTrustedTrnEvent)
        verify(eventPublisher).send(secondTrustedTrnEvent)
    }

    @Test
    fun `should send at once when called outside a transaction`() {
        val savedEvent = testCorporateEvent.copy(id = "no-transaction-event")
        whenever(positionService.findWhereHeld(any(), any())).thenReturn(PortfoliosResponse(listOf(testPortfolio)))
        whenever(positionService.process(any(), any(), anyOrNull())).thenReturn(testTrustedTrnEvent)

        eventDistribution.processEvent(savedEvent)

        verify(eventPublisher, times(1)).send(testTrustedTrnEvent)
    }
}