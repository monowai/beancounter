package com.beancounter.marketdata.macro

import com.beancounter.auth.model.AuthConstants
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Macro market context: treasury yields, oil-proxy moves, and Fed rate-decision odds.
 *
 * Thin — both endpoints delegate straight to their service; see [MacroIndicatorsService] and
 * [RateExpectationsService] for the actual composition/projection logic.
 */
@RestController
@RequestMapping("/macro")
@PreAuthorize(
    "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
)
@Tag(name = "Macro", description = "Macro market context: treasury yields, oil proxies, Fed rate-decision odds")
class MacroController(
    private val macroIndicatorsService: MacroIndicatorsService,
    private val rateExpectationsService: RateExpectationsService
) {
    @GetMapping("/indicators", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Get treasury yield and oil-proxy indicators",
        description =
            "Treasury yield curve (US10Y/US2Y) and oil-proxy price moves (WTI/Brent ETF proxies) " +
                "over the requested lookback window. Entries that fail upstream are omitted, not null."
    )
    fun getIndicators(
        @Parameter(description = "Lookback window in days", example = "14")
        @RequestParam(
            required = false,
            defaultValue = "14"
        ) lookbackDays: Int = MacroIndicatorsService.DEFAULT_LOOKBACK_DAYS
    ): MacroIndicatorsResponse = macroIndicatorsService.getIndicators(lookbackDays)

    @GetMapping("/rate-expectations", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Get Fed rate-decision market odds",
        description =
            "Implied probabilities for the nearest open Fed-decision (KXFEDDECISION) Kalshi event, " +
                "with a trend delta against the prior snapshot when one exists."
    )
    fun getRateExpectations(): RateExpectationsResponse? = rateExpectationsService.getRateExpectations()
}