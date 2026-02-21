package com.beancounter.marketdata.fx

import com.beancounter.auth.TokenService
import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.BulkFxRequest
import com.beancounter.common.contracts.BulkFxResponse
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate

/**
 * FX Market Data Controller.
 *
 * @author mikeh
 * @since 2019-01-29
 */
@RestController
@RequestMapping("/fx")
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
)
class FxController
    @Autowired
    internal constructor(
        private val fxRateService: FxRateService,
        private val tokenService: TokenService
    ) {
        @PostMapping
        fun getRates(
            @RequestBody fxRequest: FxRequest
        ): FxResponse =
            fxRateService.getRates(
                fxRequest,
                tokenService.bearerToken
            )

        @PostMapping("/bulk")
        fun getBulkRates(
            @RequestBody bulkFxRequest: BulkFxRequest
        ): BulkFxResponse = fxRateService.getBulkRates(bulkFxRequest)

        /**
         * Get available FX rate provider IDs for comparison.
         */
        @GetMapping("/providers")
        fun getProviders(): FxProvidersResponse = FxProvidersResponse(fxRateService.getAvailableProviders())

        /**
         * Get historical FX rates for a currency pair from the database cache.
         */
        @GetMapping("/history")
        fun getHistory(
            @RequestParam from: String,
            @RequestParam to: String,
            @RequestParam(defaultValue = "3") months: Int
        ): FxHistoryResponse = fxRateService.getHistoricalRates(from, to, months)
    }

/**
 * Response wrapper for available FX providers.
 */
data class FxProvidersResponse(
    val providers: List<String>
)

/**
 * Response for historical FX rate data (for charting).
 */
data class FxHistoryResponse(
    val from: String,
    val to: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val data: List<FxHistoryPoint>
)

data class FxHistoryPoint(
    val date: LocalDate,
    val rate: BigDecimal
)