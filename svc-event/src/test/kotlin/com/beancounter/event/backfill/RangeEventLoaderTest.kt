package com.beancounter.event.backfill

import com.beancounter.auth.AuthConfig
import com.beancounter.auth.client.LoginService
import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.client.services.PriceService
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.model.Positions
import com.beancounter.common.model.TrnType
import com.beancounter.event.corporate.CorporateEventStore
import com.beancounter.event.distribution.EventDistribution
import com.beancounter.event.distribution.PositionService
import com.beancounter.event.utils.TestHelpers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Loading a portfolio's events over a range: positions are read once and each held asset's
 * events are fetched once, however long the range, then the stored events in it are processed.
 */
class RangeEventLoaderTest {
    private val positionService = mock<PositionService>()
    private val loginService = mock<LoginService>()
    private val priceService = mock<PriceService>()
    private val eventStore = mock<CorporateEventStore>()
    private val eventDistribution = mock<EventDistribution>()
    private val portfolio = TestHelpers.createTestPortfolio()
    private val from = LocalDate.of(2016, 1, 4)
    private val loader =
        RangeEventLoader(
            positionService,
            AssetEventLoader(priceService, eventStore, DAYS_TO_ADD),
            eventStore,
            eventDistribution,
            loginService
        )

    private fun holding(vararg assetCodes: String) {
        val positions = Positions(portfolio)
        assetCodes.forEach {
            positions.add(TestHelpers.createTestPosition(TestHelpers.createTestAsset(code = it), portfolio))
        }
        val authConfig = mock<AuthConfig>()
        whenever(authConfig.clientSecret).thenReturn("secret")
        whenever(loginService.authConfig).thenReturn(authConfig)
        whenever(loginService.loginM2m(any())).thenReturn(OpenIdResponse("token", "scope", 60L, "Bearer"))
        whenever(positionService.getPositions(eq(portfolio), any())).thenReturn(PositionResponse(positions))
        whenever(positionService.includePosition(any())).thenReturn(true)
    }

    private fun event(
        assetId: String,
        recordDate: LocalDate
    ) = CorporateEvent(
        id = "$assetId-$recordDate",
        trnType = TrnType.DIVI,
        recordDate = recordDate,
        assetId = assetId,
        rate = BigDecimal("0.25")
    )

    @Test
    fun `should read positions once and fetch each asset's events once over a ten-year range`() {
        holding("AAA", "BBB")
        whenever(priceService.getEvents(any())).thenReturn(PriceResponse(emptyList()))

        loader.load(portfolio, from)

        verify(positionService, times(1)).getPositions(eq(portfolio), any())
        verify(priceService, times(1)).getEvents("AAA")
        verify(priceService, times(1)).getEvents("BBB")
    }

    @Test
    fun `should store the events from the start date on, and none before it`() {
        holding("AAA")
        whenever(priceService.getEvents("AAA")).thenReturn(
            PriceResponse(
                listOf(
                    TestHelpers.createTestMarketData(priceDate = from.minusDays(1)),
                    TestHelpers.createTestMarketData(priceDate = from),
                    TestHelpers.createTestMarketData(priceDate = LocalDate.of(2022, 7, 18))
                )
            )
        )

        loader.load(portfolio, from)

        verify(eventStore, times(2)).save(any())
        verify(eventStore, times(0)).save(argThat { recordDate == from.minusDays(1) })
    }

    @Test
    fun `should process every stored event in the range, and carry on past an asset that fails`() {
        holding("AAA", "BBB", "CCC")
        whenever(priceService.getEvents(any())).thenReturn(PriceResponse(emptyList()))
        whenever(priceService.getEvents("BBB")).thenThrow(IllegalStateException("provider down"))
        val aaa = event("AAA", LocalDate.of(2020, 3, 2))
        val ccc = event("CCC", LocalDate.of(2024, 9, 9))
        whenever(eventStore.findInRange(eq("AAA"), eq(from), any())).thenReturn(listOf(aaa))
        whenever(eventStore.findInRange(eq("BBB"), eq(from), any())).thenReturn(emptyList())
        whenever(eventStore.findInRange(eq("CCC"), eq(from), any())).thenReturn(listOf(ccc))

        val processed = loader.load(portfolio, from)

        assertThat(processed).isEqualTo(2)
        verify(eventDistribution).processEvent(aaa)
        verify(eventDistribution).processEvent(ccc)
    }

    private companion object {
        const val DAYS_TO_ADD = 10L
    }
}