package com.beancounter.client

import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.DateUtils

/**
 * Used to reduce duplicate object code quality warnings.
 */
class Constants {
    companion object {
        val USD = Currency("USD")
        val AUD = Currency("ASD")
        val GBP = Currency("GBP")
        val NZD = Currency("NZD")
        val SGD = Currency("SGD")
        val EUR = Currency("EUR")
        val NYSE = Market("NYSE")
        val NASDAQ = Market("NASDAQ")
        val ASX = Market("ASX", AUD.code)
        private val owner = SystemUser(
            id = "blah@blah.com",
            email = "blah@blah.com",
            true,
            since = DateUtils().getDate("2020-06-03"),
        )
        var portfolio: Portfolio = Portfolio(
            id = "TEST",
            code = "TEST",
            name = "NZD Portfolio",
            currency = NZD,
            base = USD,
            owner = owner,
        )
        const val pTradeCashRate = "tradeCashRate"
        const val pTradePortfolioRate = "tradePortfolioRate"
        const val pTradeBaseRate = "tradeBaseRate"
    }
}
