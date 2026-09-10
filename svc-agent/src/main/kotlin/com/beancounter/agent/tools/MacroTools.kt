package com.beancounter.agent.tools

import com.beancounter.agent.clients.MacroClient
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service

/**
 * Tools for quantifying the macro backdrop: treasury yield/oil-proxy moves, and market-implied
 * Fed rate-decision odds. Pairs with [NewsTools.getMarketNews]("market") — that tool explains the
 * backdrop in words, these two put numbers on it.
 */
@Service
class MacroTools(
    private val macroClient: MacroClient
) {
    private val log = LoggerFactory.getLogger(MacroTools::class.java)

    @Tool(description = INDICATORS_DESC)
    fun getMacroIndicators(
        // NOTE: Int? + body default, not a Kotlin default parameter value — see RetireTools for
        // why Spring AI's reflective tool invocation can't rely on Kotlin defaults surviving,
        // and would NPE unboxing a null into a primitive Int if this were typed non-null.
        @ToolParam(description = LOOKBACK_DESC, required = false) lookbackDays: Int? = null
    ): Map<String, Any> {
        val days = lookbackDays ?: DEFAULT_LOOKBACK_DAYS
        log.debug("getMacroIndicators called: lookbackDays={}", days)
        val raw = macroClient.getIndicators(days)
        return if (raw.isEmpty()) {
            log.debug("getMacroIndicators: no_coverage for lookbackDays={}", days)
            mapOf(
                "status" to "no_coverage",
                "message" to INDICATORS_NO_COVERAGE_MESSAGE
            )
        } else {
            raw
        }
    }

    @Tool(description = RATE_EXPECTATIONS_DESC)
    fun getRateExpectations(): Map<String, Any> {
        log.debug("getRateExpectations called")
        val raw = macroClient.getRateExpectations()
        return if (raw.isNullOrEmpty()) {
            log.debug("getRateExpectations: no_coverage (no open Fed event)")
            mapOf(
                "status" to "no_coverage",
                "message" to RATE_EXPECTATIONS_NO_COVERAGE_MESSAGE
            )
        } else {
            raw
        }
    }

    companion object {
        const val DEFAULT_LOOKBACK_DAYS = 14

        const val INDICATORS_DESC =
            "Get treasury yield levels (US10Y/US2Y) with their change in bps, and oil-proxy " +
                "(WTI/Brent) percent moves, over a lookback window. Use to QUANTIFY the macro " +
                "backdrop when the user asks about performance vs macro conditions over a " +
                "period — pick lookbackDays to match the user's window (e.g. 'last 2 weeks' " +
                "→ 14, 'last month' → 30). Pair with getMarketNews(\"market\") for the " +
                "qualitative headlines and getRateExpectations for rate-decision odds. NEVER " +
                "mention the underlying data providers."
        const val LOOKBACK_DESC =
            "Lookback window in days (default 14). Match the user's stated window: 'last 2 " +
                "weeks' → 14, 'last month' → 30, 'this year'/YTD → days since Jan 1. Omit or " +
                "pass null to use the default."
        const val INDICATORS_NO_COVERAGE_MESSAGE =
            "No macro indicator data available right now. Do not invent yield or oil figures " +
                "— say the data isn't available and continue with what you do have."

        const val RATE_EXPECTATIONS_DESC =
            "Get market-implied probabilities for the next Fed rate decision, plus how those " +
                "odds have shifted since a prior snapshot (a 'trend' key, absent when there's " +
                "no history yet). A repricing of rate expectations often explains an " +
                "equity/bond move better than the decision itself. NEVER name the venue or " +
                "data provider behind these odds."
        const val RATE_EXPECTATIONS_NO_COVERAGE_MESSAGE =
            "No open Fed rate-decision market to price right now. Do not invent probabilities " +
                "— say rate-decision odds aren't currently available."
    }
}