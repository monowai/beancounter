package com.beancounter.position.holdings

import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
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
    fun namesIds(): Boolean = !ids.isNullOrBlank()

    fun resolve(portfolioServiceClient: PortfolioServiceClient): Collection<Portfolio> {
        if (!ids.isNullOrBlank()) {
            return entries(ids)
                .mapNotNull { id ->
                    // svc-data answers 404 (NotFoundException) for a missing id or one the caller
                    // may not see, and another 4xx (BusinessException) for a rejected one; only
                    // those are swallowed. Network and auth failures propagate.
                    runCatching { portfolioServiceClient.getPortfolioById(id) }
                        .onFailure { e ->
                            if (e !is NotFoundException && e !is BusinessException) throw e
                            log.debug("Skipping portfolio {} (not visible to caller): {}", id, e.message)
                        }.getOrNull()
                }
        }
        val allPortfolios = portfolioServiceClient.portfolios.data
        if (codes.isNullOrBlank()) {
            return allPortfolios
        }
        val codeSet = entries(codes).toSet()
        return allPortfolios.filter { it.code in codeSet }
    }

    /** The comma-separated entries of [list], trimmed, blanks dropped. */
    private fun entries(list: String): List<String> = list.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        private val log = LoggerFactory.getLogger(PortfolioSelection::class.java)
    }
}