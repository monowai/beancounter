package com.beancounter.common.input

/**
 * Basic portfolio request contract. These are tied to the Caller and do not accept a primary key.
 * The Code is expected to be unique to the caller.
 */
data class PortfolioInput(val code: String, var name: String, var currency: String, var base: String) {
    constructor(code: String, name: String, currency: String) : this(code, name, currency, "USD")
}
