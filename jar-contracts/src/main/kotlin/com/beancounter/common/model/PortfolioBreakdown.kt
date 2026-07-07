package com.beancounter.common.model

import java.math.BigDecimal

/**
 * Per-portfolio contribution to an aggregated [Position].
 *
 * Populated only on the aggregated holdings endpoint so the UI can show
 * which underlying portfolios hold an asset and let the user navigate to
 * a single portfolio.
 */
data class PortfolioBreakdown(
    val portfolioId: String,
    val portfolioCode: String,
    val portfolioName: String,
    val quantity: BigDecimal,
    /** Broker name -> quantity held within THIS portfolio. */
    val held: Map<String, BigDecimal> = emptyMap()
)