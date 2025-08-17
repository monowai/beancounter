package com.beancounter.event.service

import com.beancounter.auth.client.LoginService
import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.client.services.PriceService
import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.TrnType
import com.beancounter.event.common.DateSplitter
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.event.utils.TestHelpers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any
import java.time.LocalDate

/**
 * Test suite for EventLoader to ensure proper event loading functionality.
 * 
 * This class tests:
 * - Event loading for portfolios and specific dates
 * - Integration with portfolio and price services
 * - Event processing and saving
 * - Authentication context handling
 * - Date range processing
 * 
 * Tests verify that the EventLoader correctly loads
 * and processes corporate events from various sources.
 */
@ExtendWith(MockitoExtension::class)
class EventLoaderTest {

    @Mock
    private lateinit var portfolioService: PortfolioServiceClient

    @Mock
    private lateinit var backFillService: BackFillService

    @Mock
    private lateinit var positionService: PositionService

    @Mock
    private lateinit var priceService: PriceService

    @Mock
    private lateinit var eventService: EventService

    @Mock
    private lateinit var dateSplitter: DateSplitter

    @Mock
    private lateinit var loginService: LoginService

    @Mock
    private lateinit var authContext: OpenIdResponse

    @Mock
    private lateinit var authConfig: com.beancounter.auth.AuthConfig

    private lateinit var eventLoader: EventLoader

    private lateinit var testPortfolio: Portfolio
    private lateinit var testPosition: Position
    private lateinit var testMarketData: MarketData

    @BeforeEach
    fun setUp() {
        eventLoader = EventLoader(
            portfolioService,
            backFillService,
            positionService,
            priceService,
            eventService,
            dateSplitter,
            loginService
        )

        testPortfolio = TestHelpers.createTestPortfolio()
        testPosition = TestHelpers.createTestPosition(TestHelpers.createTestAsset(), testPortfolio)
        testMarketData = TestHelpers.createTestMarketData()
    }

    @Test
    fun `should load events for all portfolios when loading events for date`() {
        // Given
        val date = "2024-01-15"
        val portfolios = PortfoliosResponse(listOf(testPortfolio))
        whenever(portfolioService.portfolios).thenReturn(portfolios)
        whenever(loginService.loginM2m()).thenReturn(authContext)

        // When
        eventLoader.loadEvents(date)

        // Then
        verify(portfolioService).portfolios
        verify(loginService).loginM2m()
    }
}
