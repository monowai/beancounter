package com.beancounter.client

import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.DateUtils

/**
 * Used to reduce duplicate object code quality warnings.
 */
class Constants {
    companion object {
        val USD = Currency("USD")
        val NZD = Currency("NZD")
        val SGD = Currency("SGD")
        private val owner = SystemUser(
            id = "blah@blah.com",
            email = "blah@blah.com",
            true,
            DateUtils().getDate("2020-06-03")
        )
        var portfolio: Portfolio = Portfolio(
            id = "TEST",
            code = "TEST",
            name = "NZD Portfolio",
            currency = NZD,
            base = USD,
            owner = owner
        )
    }
}
