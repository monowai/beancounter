package com.beancounter.agent.tools

import com.beancounter.agent.clients.MacroClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * Verify that MacroTools delegates to MacroClient correctly and surfaces the no_coverage
 * contract on empty/null upstream results, mirroring NewsToolsTest.
 */
class MacroToolsTest {
    private val indicatorsResponse: Map<String, Any> =
        mapOf(
            "asOf" to "2026-09-11",
            "lookbackDays" to 14,
            "yields" to listOf(mapOf("series" to "US10Y", "changeBps" to 12)),
            "oil" to listOf(mapOf("series" to "WTI_PROXY", "changePercent" to 3.2))
        )

    private val rateExpectationsResponse: Map<String, Any> =
        mapOf(
            "event" to "KXFEDDECISION-26SEP",
            "title" to "Fed decision",
            "outcomes" to listOf(mapOf("label" to "Hike 25bps", "probability" to 0.635))
        )

    @Test
    fun `getMacroIndicators delegates to the client with the default lookback when omitted`() {
        val client =
            mock<MacroClient> {
                on { getIndicators(14) } doReturn indicatorsResponse
            }
        val tools = MacroTools(client)

        val result = tools.getMacroIndicators()

        assertThat(result).isSameAs(indicatorsResponse)
        verify(client).getIndicators(14)
    }

    @Test
    fun `getMacroIndicators passes an explicit lookbackDays through to the client`() {
        val client =
            mock<MacroClient> {
                on { getIndicators(30) } doReturn indicatorsResponse
            }
        val tools = MacroTools(client)

        val result = tools.getMacroIndicators(30)

        assertThat(result).isSameAs(indicatorsResponse)
        verify(client).getIndicators(30)
    }

    @Test
    fun `getMacroIndicators returns no_coverage marker when the client returns an empty map`() {
        val client =
            mock<MacroClient> {
                on { getIndicators(14) } doReturn emptyMap()
            }
        val tools = MacroTools(client)

        val result = tools.getMacroIndicators()

        assertThat(result["status"]).isEqualTo("no_coverage")
        assertThat(result["message"]).isNotNull()
    }

    @Test
    fun `getMacroIndicators returns no_coverage when yields and oil are both empty lists`() {
        // /macro/indicators always returns a full object — an empty raw map never actually
        // happens in practice. The real no-coverage signal is both legs coming back empty.
        val client =
            mock<MacroClient> {
                on { getIndicators(14) } doReturn
                    mapOf(
                        "asOf" to "2026-09-11",
                        "lookbackDays" to 14,
                        "yields" to emptyList<Any>(),
                        "oil" to emptyList<Any>()
                    )
            }
        val tools = MacroTools(client)

        val result = tools.getMacroIndicators()

        assertThat(result["status"]).isEqualTo("no_coverage")
        assertThat(result["message"]).isNotNull()
    }

    @Test
    fun `getMacroIndicators passes through when only one of yields or oil has data`() {
        val partial =
            mapOf(
                "asOf" to "2026-09-11",
                "lookbackDays" to 14,
                "yields" to listOf(mapOf("series" to "US10Y", "changeBps" to 12)),
                "oil" to emptyList<Any>()
            )
        val client =
            mock<MacroClient> {
                on { getIndicators(14) } doReturn partial
            }
        val tools = MacroTools(client)

        val result = tools.getMacroIndicators()

        assertThat(result).isSameAs(partial)
    }

    @Test
    fun `getMacroIndicators clamps a negative lookbackDays up to the minimum of 1`() {
        val client =
            mock<MacroClient> {
                on { getIndicators(1) } doReturn indicatorsResponse
            }
        val tools = MacroTools(client)

        val result = tools.getMacroIndicators(-5)

        assertThat(result).isSameAs(indicatorsResponse)
        verify(client).getIndicators(1)
    }

    @Test
    fun `getMacroIndicators clamps an oversized lookbackDays down to the maximum of 365`() {
        val client =
            mock<MacroClient> {
                on { getIndicators(365) } doReturn indicatorsResponse
            }
        val tools = MacroTools(client)

        val result = tools.getMacroIndicators(9999)

        assertThat(result).isSameAs(indicatorsResponse)
        verify(client).getIndicators(365)
    }

    @Test
    fun `getRateExpectations delegates to the client and passes the payload through untouched`() {
        val client =
            mock<MacroClient> {
                on { getRateExpectations() } doReturn rateExpectationsResponse
            }
        val tools = MacroTools(client)

        val result = tools.getRateExpectations()

        assertThat(result).isSameAs(rateExpectationsResponse)
        verify(client).getRateExpectations()
    }

    @Test
    fun `getRateExpectations returns no_coverage marker when the client returns null`() {
        val client =
            mock<MacroClient> {
                on { getRateExpectations() } doReturn null
            }
        val tools = MacroTools(client)

        val result = tools.getRateExpectations()

        assertThat(result["status"]).isEqualTo("no_coverage")
        assertThat(result["message"]).isNotNull()
    }
}