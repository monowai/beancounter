package com.beancounter.common.utils

import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio

/**
 * Test Helper for dealing with Portfolios.
 */
class PortfolioUtils private constructor() {
    companion object {
        @JvmStatic
        fun getPortfolioInput(code: String): PortfolioInput {
            return PortfolioInput(code, code, "USD", "NZD")
        }

        @JvmStatic
        fun getPortfolio(code: String = DateUtils.today): Portfolio {
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
