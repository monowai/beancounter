package com.beancounter.position.valuation

import com.beancounter.auth.model.AuthConstants
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.DateUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * Admin controller for manually triggering portfolio valuations.
 * Provides the same functionality as the scheduled valuation task
 * but accessible on-demand by administrators.
 */
@RestController
@RequestMapping("/valuations")
@CrossOrigin
@PreAuthorize("hasAuthority('${AuthConstants.SCOPE_ADMIN}')")
@Tag(
    name = "Valuation Administration",
    description = "Admin operations for manually triggering portfolio valuations"
)
class ValuationAdminController(
    private val portfolioServiceClient: PortfolioServiceClient,
    private val valuationService: Valuation,
    private val dateUtils: DateUtils,
    @Value($$"${valuation.stale.hours:24}") private val staleHours: Long = 24
) {
    private val log = LoggerFactory.getLogger(ValuationAdminController::class.java)

    /**
     * Response for the revalue operation.
     */
    data class RevalueResponse(
        val totalPortfolios: Int,
        val stalePortfolios: Int,
        val successCount: Int,
        val errorCount: Int,
        val errors: List<String> = emptyList()
    )

    @PostMapping(
        value = ["/revalue"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Revalue stale portfolios",
        description = """
            Manually triggers portfolio revaluation for all portfolios that haven't
            been valued in the last 24 hours. This is the same logic used by the
            scheduled valuation task.

            Use this to:
            * Force portfolio valuations outside the scheduled window
            * Update portfolio market values after market data refresh
            * Debug valuation issues

            Requires admin privileges.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Revaluation completed",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = RevalueResponse::class)
                    )
                ]
            ),
            ApiResponse(
                responseCode = "403",
                description = "Admin privileges required"
            )
        ]
    )
    fun revalueStalePortfolios(
        @RequestParam(
            value = "force",
            defaultValue = "false"
        ) force: Boolean
    ): RevalueResponse {
        log.info("Manual portfolio revaluation triggered (force={})", force)

        val allPortfolios = portfolioServiceClient.portfolios.data

        if (allPortfolios.isEmpty()) {
            log.info("No portfolios in system")
            return RevalueResponse(
                totalPortfolios = 0,
                stalePortfolios = 0,
                successCount = 0,
                errorCount = 0
            )
        }

        val portfoliosToValue =
            if (force) {
                allPortfolios
            } else {
                allPortfolios.filter { needsValuation(it) }
            }

        if (portfoliosToValue.isEmpty()) {
            log.info("All {} portfolios are up to date", allPortfolios.size)
            return RevalueResponse(
                totalPortfolios = allPortfolios.size,
                stalePortfolios = 0,
                successCount = 0,
                errorCount = 0
            )
        }

        log.info(
            "Revaluing {} portfolios out of {} total (force={})",
            portfoliosToValue.size,
            allPortfolios.size,
            force
        )

        var successCount = 0
        var errorCount = 0
        val errors = mutableListOf<String>()

        for (portfolio in portfoliosToValue) {
            try {
                valuationService.getPositions(
                    portfolio,
                    DateUtils.TODAY,
                    true
                )
                successCount++
                log.debug(
                    "Valued portfolio: {} ({}) - last valued: {}",
                    portfolio.code,
                    portfolio.id,
                    portfolio.valuedAt
                )
            } catch (e: Exception) {
                errorCount++
                val errorMsg = "${portfolio.code}: ${e.message}"
                errors.add(errorMsg)
                log.error(
                    "Failed to value portfolio: {} ({}): {}",
                    portfolio.code,
                    portfolio.id,
                    e.message
                )
            }
        }

        log.info(
            "Manual portfolio revaluation completed: {} success, {} errors",
            successCount,
            errorCount
        )

        return RevalueResponse(
            totalPortfolios = allPortfolios.size,
            stalePortfolios = portfoliosToValue.size,
            successCount = successCount,
            errorCount = errorCount,
            errors = errors
        )
    }

    /**
     * Determines if a portfolio needs valuation based on when it was last valued.
     */
    private fun needsValuation(portfolio: Portfolio): Boolean {
        val valuedAt = portfolio.valuedAt ?: return true
        val staleDate = LocalDate.now(dateUtils.zoneId).minusDays(staleHours / 24)
        return valuedAt.isBefore(staleDate)
    }
}