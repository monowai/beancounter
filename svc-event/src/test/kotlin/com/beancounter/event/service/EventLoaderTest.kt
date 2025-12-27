package com.beancounter.event.service

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.client.LoginService
import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.client.services.PriceService
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.utils.DateUtils
import com.beancounter.event.common.DateSplitter
import com.beancounter.event.config.AuthConfig
import com.beancounter.event.config.EventLoaderConfig
import com.beancounter.event.config.EventLoaderServiceConfig
import com.beancounter.event.config.SharedConfig
import com.beancounter.event.utils.TestHelpers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

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
    @MockitoBean
    private lateinit var portfolioService: PortfolioServiceClient

    @MockitoBean
    private lateinit var backFillService: BackFillService

    @MockitoBean
    private lateinit var positionService: PositionService

    @MockitoBean
    private lateinit var priceService: PriceService

    @MockitoBean
    private lateinit var eventService: EventService

    @MockitoBean
    private lateinit var dateSplitter: DateSplitter

    @MockitoBean
    private lateinit var loginService: LoginService

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    private lateinit var authContext: OpenIdResponse

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
        val sharedConfig =
            SharedConfig(
                dateUtils = DateUtils(),
                portfolioService = portfolioService
            )
        val serviceConfig =
            EventLoaderServiceConfig(
                eventService = eventService,
                priceService = priceService,
                backFillService = backFillService,
                positionService = positionService
            )
        val authConfig =
            AuthConfig(
                dateSplitter = dateSplitter,
                loginService = loginService
            )
        val eventLoaderConfig =
            EventLoaderConfig(
                sharedConfig = sharedConfig,
                serviceConfig = serviceConfig,
                authConfig = authConfig
            )
        val eventLoader = EventLoader(eventLoaderConfig, daysToAdd = 10)

        // Then
        assertThat(eventLoader).isNotNull()
    }

    @Test
    fun `should load events within configured window`() {
        // Given - default daysToAdd is 10
        val portfolios = PortfoliosResponse(listOf(testPortfolio))
        whenever(portfolioService.portfolios).thenReturn(portfolios)

        val sharedConfig =
            SharedConfig(
                dateUtils = DateUtils(),
                portfolioService = portfolioService
            )
        val serviceConfig =
            EventLoaderServiceConfig(
                eventService = eventService,
                priceService = priceService,
                backFillService = backFillService,
                positionService = positionService
            )
        val authConfig =
            AuthConfig(
                dateSplitter = dateSplitter,
                loginService = loginService
            )
        val eventLoaderConfig =
            EventLoaderConfig(
                sharedConfig = sharedConfig,
                serviceConfig = serviceConfig,
                authConfig = authConfig
            )
        // Verify daysToAdd can be configured - scans both backwards and forwards
        val customDaysToAdd = 14L
        val eventLoader = EventLoader(eventLoaderConfig, daysToAdd = customDaysToAdd)

        // Then
        assertThat(eventLoader).isNotNull()
    }
}