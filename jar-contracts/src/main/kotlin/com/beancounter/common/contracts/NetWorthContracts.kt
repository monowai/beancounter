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
    /** [gainOnDay] against the previous day's net worth, in percentage points. */
    val gainOnDayPercent: BigDecimal = BigDecimal.ZERO,
    /**
     * Rolling 7-day move of the current holdings: each priced holding's value now against its
     * close 7 days before (the trading day on or before), at today's FX, as [gainOnDay] is.
     * Null when unknown: the week-ago prices could not be read, or none came back.
     */
    val weekChange: BigDecimal? = null,
    /**
     * [weekChange] in percentage points, against [totalValue] less [weekChange]: net worth with
     * the current holdings at their week-ago closes. Null when [weekChange] is.
     */
    val weekChangePercent: BigDecimal? = null,
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
    val irr: BigDecimal,
    /** Change on day at the portfolio's last valuation, in the target currency. */
    val gainOnDay: BigDecimal = BigDecimal.ZERO,
    /** [gainOnDay] against the portfolio's previous value, in percentage points. */
    val gainOnDayPercent: BigDecimal = BigDecimal.ZERO
)

data class NetWorthResponse(
    override val data: NetWorth
) : Payload<NetWorth>