package com.beancounter.event.distribution

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.event.utils.TestHelpers
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * EventDistribution through its transactional proxy: transaction events leave only once
 * the distribution's transaction commits.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockAuth
class EventDistributionCommitTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var positionService: PositionService

    @MockitoBean
    private lateinit var eventPublisher: EventPublisher

    @Autowired
    private lateinit var eventDistribution: EventDistribution

    private val event = TestHelpers.createTestCorporateEvent().copy(id = "commit-test-event")
    private val firstPortfolio = TestHelpers.createTestPortfolio(id = "first-portfolio")
    private val secondPortfolio = TestHelpers.createTestPortfolio(id = "second-portfolio")
    private val firstTrnEvent = TestHelpers.createTestTrustedTrnEvent(firstPortfolio)
    private val secondTrnEvent = TestHelpers.createTestTrustedTrnEvent(secondPortfolio)

    @Test
    fun `should send nothing when a later portfolio fails`() {
        whenever(positionService.findWhereHeld(any(), any()))
            .thenReturn(PortfoliosResponse(listOf(firstPortfolio, secondPortfolio)))
        whenever(positionService.process(eq(firstPortfolio), any(), anyOrNull())).thenReturn(firstTrnEvent)
        whenever(positionService.process(eq(secondPortfolio), any(), anyOrNull()))
            .thenThrow(BusinessException("position lookup failed"))

        assertThatThrownBy { eventDistribution.processEvent(event) }
            .isInstanceOf(BusinessException::class.java)

        verify(eventPublisher, never()).send(any())
    }

    @Test
    fun `should send every event once after commit`() {
        whenever(positionService.findWhereHeld(any(), any()))
            .thenReturn(PortfoliosResponse(listOf(firstPortfolio, secondPortfolio)))
        whenever(positionService.process(eq(firstPortfolio), any(), anyOrNull())).thenReturn(firstTrnEvent)
        whenever(positionService.process(eq(secondPortfolio), any(), anyOrNull())).thenReturn(secondTrnEvent)

        eventDistribution.processEvent(event)

        verify(eventPublisher, times(1)).send(firstTrnEvent)
        verify(eventPublisher, times(1)).send(secondTrnEvent)
    }

    @Test
    fun `should still send the other events when one send fails`() {
        whenever(positionService.findWhereHeld(any(), any()))
            .thenReturn(PortfoliosResponse(listOf(firstPortfolio, secondPortfolio)))
        whenever(positionService.process(eq(firstPortfolio), any(), anyOrNull())).thenReturn(firstTrnEvent)
        whenever(positionService.process(eq(secondPortfolio), any(), anyOrNull())).thenReturn(secondTrnEvent)
        whenever(eventPublisher.send(firstTrnEvent)).thenThrow(IllegalStateException("broker down"))

        eventDistribution.processEvent(event)

        verify(eventPublisher, times(1)).send(secondTrnEvent)
    }
}