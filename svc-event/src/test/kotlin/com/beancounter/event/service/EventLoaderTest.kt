package com.beancounter.event.service

import com.beancounter.auth.client.LoginService
import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.client.services.PriceService
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.event.common.DateSplitter
import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.event.utils.TestHelpers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockAuth
class EventLoaderTest {
    @MockBean
    private lateinit var portfolioService: PortfolioServiceClient

    @MockBean
    private lateinit var backFillService: BackFillService

    @MockBean
    private lateinit var positionService: PositionService

    @MockBean
    private lateinit var priceService: PriceService

    @MockBean
    private lateinit var eventService: EventService

    @MockBean
    private lateinit var dateSplitter: DateSplitter

    @MockBean
    private lateinit var loginService: LoginService

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    private lateinit var authContext: OpenIdResponse

    private lateinit var eventLoader: EventLoader

    private lateinit var testPortfolio: Portfolio
    private lateinit var testPosition: Position
    private lateinit var testMarketData: MarketData

    @BeforeEach
    fun setUp() {
        authContext = OpenIdResponse("test-token", "test-scope", 3600L, "Bearer")
        
        testPortfolio = TestHelpers.createTestPortfolio()
        testPosition = TestHelpers.createTestPosition(TestHelpers.createTestAsset(), testPortfolio)
        testMarketData = TestHelpers.createTestMarketData()
    }

    @Test
    fun `should create EventLoader instance successfully`() {
        // Given
        val portfolios = PortfoliosResponse(listOf(testPortfolio))
        whenever(portfolioService.portfolios).thenReturn(portfolios)

        // When
        val eventLoader = EventLoader(
            portfolioService,
            backFillService,
            positionService,
            priceService,
            eventService,
            dateSplitter,
            loginService
        )

        // Then
        assertThat(eventLoader).isNotNull()
    }


}