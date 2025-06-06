package com.beancounter.position

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.DateUtils
import java.math.BigDecimal

private const val BALANCE = "BALANCE"

/**
 * Used to reduce duplicate object code quality warnings.
 */
class Constants {
    companion object {
        const val TEST = "TEST"
        val fourK = BigDecimal("4000.00")

        val twoK = BigDecimal("2000.00")

        val ten = BigDecimal("10.00")

        val hundred = BigDecimal(100)

        val USD = Currency("USD")
        val AUD = Currency("AUD")
        val NZD = Currency("NZD")
        val GBP = Currency("GBP")
        val SGD = Currency("SGD")
        val NASDAQ = Market("NASDAQ")
        val US = Market("US")
        val CASH = Market("CASH")
        const val KMI = "KMI"
        const val AAPL = "AAPL"
        val nzdCashBalance =
            Asset(
                code = "${NZD.code} $BALANCE",
                id = "${NZD.code} $BALANCE",
                name = "${NZD.code} Balance",
                market =
                    Market(
                        CASH.code,
                        NZD.code
                    ),
                priceSymbol = NZD.code,
                category = CASH.code
            )
        val usdCashBalance =
            Asset(
                code = "${USD.code} $BALANCE",
                id = "${USD.code} $BALANCE",
                name = "${NZD.code} Balance",
                market = Market(CASH.code),
                priceSymbol = USD.code,
                category = CASH.code
            )

        const val PROP_PURCHASES = "purchases"
        const val PROP_COST_BASIS = "costBasis"
        const val PROP_SALES = "sales"

        const val PROP_COST_VALUE = "costValue"
        const val PROP_AVERAGE_COST = "averageCost"
        const val PROP_CURRENCY = "currency"
        const val PROP_SOLD = "sold"
        const val PROP_PURCHASED = "purchased"
        const val PROP_TOTAL = "total"

        const val PROP_REALIZED_GAIN = "realisedGain"

        private const val ID = "blah@blah.com"
        val owner =
            SystemUser(
                id = ID,
                email = ID,
                true,
                since = DateUtils().getFormattedDate("2020-03-08")
            )
    }
}