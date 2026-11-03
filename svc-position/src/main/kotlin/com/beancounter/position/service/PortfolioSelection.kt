package com.beancounter.position.service

import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Portfolio
import io.swagger.v3.oas.annotations.Parameter
import org.slf4j.LoggerFactory

/**
 * The portfolios a cross-portfolio read covers, as the `ids` and `codes` query
 * parameters name them. `ids` wins over `codes`: each id is fetched through
 * svc-data so visibility is delegated to its canView (owners, accepted shares,
 * M2M SYSTEM), and ids the caller cannot see are skipped. Codes filter the
 * caller's own portfolio list; neither means everything.
 */
data class PortfolioSelection(
    @field:Parameter(
        description =
            "Comma-separated portfolio ids to include. Takes precedence over `codes`. " +
                "Per-id resolution via portfolioServiceClient.getPortfolioById delegates " +
                "visibility to svc-data's canView — so owner / accepted-share callers get " +
                "their portfolios and M2M callers get the SYSTEM short-circuit. Use this " +
                "when codes can collide across users (a shared-plan viewer may own a " +
                "portfolio with the same code as the plan owner's).",
        example = "id-1,id-2"
    )
    val ids: String? = null,
    @field:Parameter(
        description = "Comma-separated portfolio codes to include. If empty, all portfolios are included.",
        example = "PORTFOLIO1,PORTFOLIO2"
    )
    val codes: String? = null
) {
    /** Whether the caller named portfolios by id. */
    val namesIds: Boolean get() = !ids.isNullOrBlank()

    fun resolve(portfolioServiceClient: PortfolioServiceClient): Collection<Portfolio> {
        if (!ids.isNullOrBlank()) {
            return ids
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { id ->
                    try {
                        portfolioServiceClient.getPortfolioById(id)
                    } catch (e: BusinessException) {
                        // Only "not visible / not found" is swallowed; network and auth
                        // failures propagate rather than read as missing ids.
                        log.debug("Skipping portfolio {} (not visible to caller): {}", id, e.message)
                        null
                    }
                }
        }
        val allPortfolios = portfolioServiceClient.portfolios.data
        if (codes.isNullOrBlank()) {
            return allPortfolios
        }
        val codeSet = codes.split(",").map { it.trim() }.toSet()
        return allPortfolios.filter { it.code in codeSet }
    }

    companion object {
        private val log = LoggerFactory.getLogger(PortfolioSelection::class.java)
    }
}