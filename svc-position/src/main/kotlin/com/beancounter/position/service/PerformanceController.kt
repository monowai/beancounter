package com.beancounter.position.service

import com.beancounter.auth.model.AuthConstants
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.client.services.StaticService
import com.beancounter.common.contracts.AggregatedPerformanceRequest
import com.beancounter.common.contracts.AggregatedPerformanceResponse
import com.beancounter.common.contracts.PerformanceResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.position.cache.PerformanceCacheService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for portfolio performance calculations (TWR).
 */
@RestController
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('${AuthConstants.SCOPE_USER}', '${AuthConstants.SCOPE_SYSTEM}')"
)
@Tag(
    name = "Performance",
    description = "Portfolio performance and time-weighted return calculations"
)
class PerformanceController(
    private val portfolioServiceClient: PortfolioServiceClient,
    private val performanceService: PerformanceService,
    private val performanceCacheService: PerformanceCacheService,
    private val benchmarkService: BenchmarkService,
    private val staticService: StaticService
) {
    private val log = LoggerFactory.getLogger(PerformanceController::class.java)

    @GetMapping("/{code}/performance")
    @Operation(
        summary = "Get portfolio performance",
        description = "Calculates time-weighted return (TWR) and growth series for a single portfolio"
    )
    fun getPerformance(
        @Parameter(description = "Portfolio code", example = "MY_PORTFOLIO")
        @PathVariable
        code: String,
        @Parameter(description = "Number of months to look back", example = "12")
        @RequestParam(value = "months", defaultValue = "12")
        months: Int
    ): PerformanceResponse {
        log.debug("Performance request for portfolio={}, months={}", code, months)
        val portfolio = portfolioServiceClient.getPortfolioByCode(code)
        return performanceService.calculate(portfolio, months)
    }

    @GetMapping("/{code}/performance/benchmark")
    @Operation(
        summary = "Get index benchmark series for a portfolio",
        description =
            "Returns a Growth-of-1000 series for the supplied index asset, " +
                "aligned to the portfolio performance series' monthly date grid."
    )
    fun getBenchmark(
        @Parameter(description = "Portfolio code", example = "MY_PORTFOLIO")
        @PathVariable
        code: String,
        @Parameter(description = "Index asset id (INDEX market)", example = "^GSPC")
        @RequestParam(value = "indexAssetId")
        indexAssetId: String,
        @Parameter(description = "Number of months to look back", example = "12")
        @RequestParam(value = "months", defaultValue = "12")
        months: Int
    ): PerformanceResponse {
        log.debug(
            "Benchmark request portfolio={} indexAssetId={} months={}",
            code,
            indexAssetId,
            months
        )
        val portfolio = portfolioServiceClient.getPortfolioByCode(code)
        return benchmarkService.benchmark(portfolio, indexAssetId, months)
    }

    @PostMapping("/performance/aggregate")
    @Operation(
        summary = "Aggregate composite TWR across portfolios",
        description =
            "Combines per-portfolio TWR series into a single display-currency series using " +
                "chained sub-period AUM-weighted composite TWR (GIPS). Period-relative " +
                "netContributions / cumulativeDividends / investmentGain are baselined from the " +
                "first point in the requested window."
    )
    fun aggregate(
        @RequestBody request: AggregatedPerformanceRequest
    ): AggregatedPerformanceResponse {
        val displayCurrency =
            staticService.getCurrency(request.displayCurrency)
                ?: throw BusinessException("Unknown display currency ${request.displayCurrency}")
        val portfolios = request.portfolioCodes.map { portfolioServiceClient.getPortfolioByCode(it) }
        log.debug(
            "Aggregate performance: portfolios={}, months={}, displayCurrency={}",
            request.portfolioCodes,
            request.months,
            request.displayCurrency
        )
        return performanceService.aggregate(portfolios, request.months, displayCurrency)
    }

    @DeleteMapping("/{code}/performance/cache")
    @Operation(summary = "Reset performance cache for a portfolio")
    fun resetCache(
        @Parameter(description = "Portfolio code", example = "MY_PORTFOLIO")
        @PathVariable
        code: String
    ): Map<String, String> {
        val portfolio = portfolioServiceClient.getPortfolioByCode(code)
        performanceCacheService.invalidatePortfolio(portfolio.id)
        log.info("Performance cache reset for portfolio={}", code)
        return mapOf("status" to "ok", "portfolio" to code)
    }
}