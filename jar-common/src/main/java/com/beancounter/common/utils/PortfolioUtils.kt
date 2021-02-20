package com.beancounter.common.utils

import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio

class PortfolioUtils private constructor() {
    companion object {
        @JvmStatic
        fun getPortfolioInput(code: String): PortfolioInput {
            return PortfolioInput(code, code, "NZD", "USD")
        }

        @JvmStatic
        fun getPortfolio(code: String): Portfolio {
            return getPortfolio(code, Currency("NZD"))
        }

        @JvmStatic
        fun getPortfolio(code: String, currency: Currency): Portfolio {
            return Portfolio(code, currency, Currency("USD"))
        }

        @JvmStatic
        fun getPortfolio(code: String, name: String, currency: Currency): Portfolio {
            return Portfolio(code, code, name, currency, Currency("USD"))
        }
    }

    init {
        throw UnsupportedOperationException("This is a utility class and cannot be instantiated")
    }
}
