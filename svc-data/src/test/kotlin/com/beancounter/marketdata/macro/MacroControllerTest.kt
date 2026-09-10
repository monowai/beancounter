package com.beancounter.marketdata.macro

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Thin-controller tests: [MacroController] only parses request params and delegates.
 * Mirrors [com.beancounter.marketdata.providers.alpha.AlphaNewsControllerTest] — mocked
 * services, direct construction, no MockMvc.
 */
internal class MacroControllerTest {
    private val indicatorsService = mock<MacroIndicatorsService>()
    private val rateExpectationsService = mock<RateExpectationsService>()
    private val controller = MacroController(indicatorsService, rateExpectationsService)

    @Test
    fun `getIndicators delegates the requested lookbackDays to the indicators service`() {
        val expected = MacroIndicatorsResponse(LocalDate.now(), 30, emptyList(), emptyList())
        whenever(indicatorsService.getIndicators(30)).thenReturn(expected)

        val result = controller.getIndicators(30)

        assertThat(result).isSameAs(expected)
        verify(indicatorsService).getIndicators(eq(30))
    }

    @Test
    fun `getIndicators defaults lookbackDays to 14 when the caller omits it`() {
        val expected = MacroIndicatorsResponse(LocalDate.now(), 14, emptyList(), emptyList())
        whenever(indicatorsService.getIndicators(14)).thenReturn(expected)

        val result = controller.getIndicators()

        assertThat(result).isSameAs(expected)
        verify(indicatorsService).getIndicators(eq(14))
    }

    @Test
    fun `getRateExpectations delegates to the rate expectations service`() {
        val expected =
            RateExpectationsResponse(
                event = "KXFEDDECISION-26SEP",
                title = "Fed decision",
                closeTime = OffsetDateTime.now(),
                outcomes = listOf(RateOutcome("Hold", BigDecimal("0.5"), null))
            )
        whenever(rateExpectationsService.getRateExpectations()).thenReturn(expected)

        val result = controller.getRateExpectations()

        assertThat(result).isSameAs(expected)
    }

    @Test
    fun `getRateExpectations returns null when there is no current event`() {
        whenever(rateExpectationsService.getRateExpectations()).thenReturn(null)

        val result = controller.getRateExpectations()

        assertThat(result).isNull()
    }
}