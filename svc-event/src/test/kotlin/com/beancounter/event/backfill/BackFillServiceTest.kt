package com.beancounter.event.backfill

import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.model.Positions
import com.beancounter.event.corporate.CorporateEventStore
import com.beancounter.event.distribution.EventDistribution
import com.beancounter.event.distribution.PositionService
import com.beancounter.event.metrics.EventMetrics
import com.beancounter.event.utils.TestHelpers
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate

class BackFillServiceTest {
    private val portfolioService = mock<PortfolioServiceClient>()
    private val positionService = mock<PositionService>()
    private val eventStore = mock<CorporateEventStore>()
    private val eventDistribution = mock<EventDistribution>()
    private val dateSplitter = mock<DateSplitter>()
    private val meterRegistry = SimpleMeterRegistry()

    private val backFillService =
        BackFillService(
            portfolioService,
            positionService,
            eventStore,
            eventDistribution,
            dateSplitter,
            EventMetrics(meterRegistry)
        )

    @Test
    fun `should count each backfilled event once`() {
        val portfolio = TestHelpers.createTestPortfolio()
        val date = LocalDate.parse(TestHelpers.TEST_DATE)
        val positions = Positions(portfolio)
        positions.add(TestHelpers.createTestPosition(portfolio = portfolio))
        whenever(portfolioService.getPortfolioById(portfolio.id)).thenReturn(portfolio)
        whenever(dateSplitter.dateRange(TestHelpers.TEST_DATE, TestHelpers.TEST_DATE)).thenReturn(listOf(date))
        whenever(positionService.getPositions(portfolio, TestHelpers.TEST_DATE)).thenReturn(PositionResponse(positions))
        whenever(positionService.includePosition(any())).thenReturn(true)
        whenever(eventStore.find(any(), any())).thenReturn(
            List(EVENTS) { TestHelpers.createTestCorporateEvent() }
        )

        backFillService.backFillEvents(portfolio.id, TestHelpers.TEST_DATE)

        assertThat(meterRegistry.counter("beancounter.event.backfill.events.loaded").count())
            .isEqualTo(EVENTS.toDouble())
    }

    private companion object {
        const val EVENTS = 3
    }
}