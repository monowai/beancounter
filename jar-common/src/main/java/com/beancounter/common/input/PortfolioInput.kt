package com.beancounter.common.input

data class PortfolioInput(val code: String, var name: String, var currency: String, var base: String) {
    constructor(code: String, name: String, currency: String) : this(code, name, currency, "USD")
}