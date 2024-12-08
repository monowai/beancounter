package com.beancounter.common.utils

import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio

/**
 * Test Helper for dealing with Portfolios.
 */
class PortfolioUtils private constructor() {
    companion object {
        private const val USD = "USD"

        private const val NZD = "NZD"

        @JvmStatic
        fun getPortfolioInput(code: String): PortfolioInput =
            PortfolioInput(
                code,
                code,
                USD,
                NZD,
            )

        @JvmStatic
        fun getPortfolio(code: String = DateUtils.TODAY): Portfolio =
            getPortfolio(
                code,
                Currency(NZD),
            )

        @JvmStatic
        fun getPortfolio(
            code: String,
            currency: Currency,
        ): Portfolio =
            Portfolio(
                id = code,
                code = code,
                name = code,
                currency = currency,
                base = Currency(USD),
            )

        @JvmStatic
        fun getPortfolio(
            code: String,
            name: String,
            currency: Currency,
        ): Portfolio =
            Portfolio(
                id = code,
                code = code,
                name = name,
                currency = currency,
                base = Currency(USD),
            )
    }
}
