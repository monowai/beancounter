package com.beancounter.common.input

/**
 * Basic portfolio request contract. These are tied to the Caller and do not accept a primary key.
 * The Code is expected to be unique to the caller.
 */
data class PortfolioInput(
    val code: String,
    var name: String = code,
    var base: String = "USD",
    var currency: String = base,
    var active: Boolean = true,
    // Funding portfolio for cash auto-settle. See Portfolio.cashPortfolioId.
    // null = no override (fall back to owner.cashPortfolioId; null on both
    // disables auto-settle).
    var cashPortfolioId: String? = null
)