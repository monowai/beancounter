package com.beancounter.event.backfill

import com.beancounter.auth.AuthConfig
import com.beancounter.auth.client.LoginService
import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.client.services.PriceService
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Positions
import com.beancounter.common.utils.DateUtils
import com.beancounter.event.corporate.CorporateEventStore
import com.beancounter.event.distribution.PositionService
import com.beancounter.event.utils.TestHelpers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockingDetails
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate

/**
 * Loading a portfolio's events: every held asset's events are stored, and the
 * portfolio's stored events are backfilled once per date, not once per asset.
 */
class EventLoaderTest {
    private val portfolioService = mock<PortfolioServiceClient>()
    private val positionService = mock<PositionService>()
    private val backFillService = mock<BackFillService>()
    private val dateSplitter = mock<DateSplitter>()
    private val loginService = mock<LoginService>()
    private val priceService = mock<PriceService>()
    private val eventStore = mock<CorporateEventStore>()
    private val date = LocalDate.parse(TestHelpers.TEST_DATE)
    private val portfolio = TestHelpers.createTestPortfolio()

    private val eventLoader =
        EventLoader(
            portfolioService,
            positionService,
            backFillService,
            dateSplitter,
            loginService,
            AssetEventLoader(priceService, eventStore, DAYS_TO_ADD)
        )

    @Test
    fun `should backfill a portfolio once per date when several assets load events`() {
        holding("AAA", "BBB", "CCC")
        whenever(priceService.getEvents(any())).thenReturn(
            PriceResponse(listOf(TestHelpers.createTestMarketData(priceDate = date)))
        )

        eventLoader.loadEvents(portfolio.id, TestHelpers.TEST_DATE)

        assertThat(invocations(eventStore, "save")).isEqualTo(3)
        assertThat(invocations(backFillService, "backFillEvents")).isEqualTo(1)
    }

    @Test
    fun `should still load other assets and backfill when one asset fails`() {
        holding("AAA", "BBB", "CCC")
        whenever(priceService.getEvents(any())).thenReturn(
            PriceResponse(listOf(TestHelpers.createTestMarketData(priceDate = date)))
        )
        whenever(priceService.getEvents("BBB")).thenThrow(IllegalStateException("provider down"))

        eventLoader.loadEvents(portfolio.id, TestHelpers.TEST_DATE)

        assertThat(invocations(eventStore, "save")).isEqualTo(2)
        assertThat(invocations(backFillService, "backFillEvents")).isEqualTo(1)
    }

    @Test
    fun `should not backfill when no asset loads an event`() {
        holding("AAA", "BBB")
        whenever(priceService.getEvents(any())).thenReturn(PriceResponse(emptyList()))

        eventLoader.loadEvents(portfolio.id, TestHelpers.TEST_DATE)

        assertThat(invocations(backFillService, "backFillEvents")).isZero()
    }

    @Test
    fun `should store only events inside the window either side of the date`() {
        whenever(priceService.getEvents(ASSET)).thenReturn(
            PriceResponse(
                listOf(
                    TestHelpers.createTestMarketData(priceDate = date.minusDays(DAYS_TO_ADD + 1)),
                    TestHelpers.createTestMarketData(priceDate = date.minusDays(DAYS_TO_ADD)),
                    TestHelpers.createTestMarketData(priceDate = date.plusDays(DAYS_TO_ADD)),
                    TestHelpers.createTestMarketData(priceDate = date.plusDays(DAYS_TO_ADD + 1))
                )
            )
        )

        val loaded = AssetEventLoader(priceService, eventStore, DAYS_TO_ADD).load(ASSET, date)

        assertThat(loaded).isEqualTo(2)
        assertThat(invocations(eventStore, "save")).isEqualTo(2)
    }

    private fun holding(vararg assetCodes: String) {
        val positions = Positions(portfolio)
        assetCodes.forEach {
            positions.add(TestHelpers.createTestPosition(TestHelpers.createTestAsset(code = it), portfolio))
        }
        whenever(portfolioService.getPortfolioById(portfolio.id)).thenReturn(portfolio)
        val authConfig = mock<AuthConfig>()
        whenever(authConfig.clientSecret).thenReturn("secret")
        whenever(loginService.authConfig).thenReturn(authConfig)
        whenever(loginService.loginM2m(any())).thenReturn(OpenIdResponse("token", "scope", 60L, "Bearer"))
        whenever(dateSplitter.dateRange(TestHelpers.TEST_DATE, DateUtils.TODAY)).thenReturn(listOf(date))
        whenever(positionService.getPositions(portfolio, date.toString())).thenReturn(PositionResponse(positions))
        whenever(positionService.includePosition(any())).thenReturn(true)
    }

    private fun invocations(
        mock: Any,
        method: String
    ): Int = mockingDetails(mock).invocations.count { it.method.name == method }

    private companion object {
        const val ASSET = "AAA"
        const val DAYS_TO_ADD = 10L
    }
}