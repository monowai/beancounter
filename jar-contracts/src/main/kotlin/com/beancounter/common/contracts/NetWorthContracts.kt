package com.beancounter.common.contracts

import java.math.BigDecimal

/**
 * Net Worth headline across a user's portfolios, in a single target
 * currency. Server-side owner of the derivation bc-view's useWealthSummary
 * used to do in the browser, so thin clients (mobile) never re-derive it.
 *
 * Reconciliation: [totalValue] = [holdingsValue] + [standaloneCompositeValue].
 * [healthcareReserve] is an informational subset (CPF MA style statutory
 * balances) and is NOT added — a held composite parent already carries it.
 */
data class NetWorth(
    val asAt: String,
    val currency: String,
    val totalValue: BigDecimal = BigDecimal.ZERO,
    /** Live aggregated positions total (PORTFOLIO view) in [currency]. */
    val holdingsValue: BigDecimal = BigDecimal.ZERO,
    /** Non-reserve sub-account balances of composite configs with no parent position. */
    val standaloneCompositeValue: BigDecimal = BigDecimal.ZERO,
    /** All MA sub-account balances across composite configs, in [currency]. */
    val healthcareReserve: BigDecimal = BigDecimal.ZERO,
    val gainOnDay: BigDecimal = BigDecimal.ZERO,
    val portfolioCount: Int = 0,
    /** Liquidity-group breakdown of held positions, sorted by value descending. */
    val classificationBreakdown: List<NetWorthClassification> = emptyList(),
    /** Per-portfolio rows in resolution order; the client sorts. */
    val portfolios: List<NetWorthPortfolio> = emptyList()
)

data class NetWorthClassification(
    val classification: String,
    val value: BigDecimal,
    val percentage: BigDecimal
)

data class NetWorthPortfolio(
    val id: String,
    val code: String,
    val name: String,
    val value: BigDecimal,
    val percentage: BigDecimal,
    val irr: BigDecimal
)

data class NetWorthResponse(
    override val data: NetWorth
) : Payload<NetWorth>