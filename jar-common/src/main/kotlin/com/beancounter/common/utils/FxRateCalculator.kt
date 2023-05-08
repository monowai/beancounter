package com.beancounter.common.utils

import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * For the supplied Pairs, compute the cross rates using the supplied rate table data.
 * Returns one rate for every requested CurrencyPair.
 *
 * You should multiply your amount by the rateMap returned by this service.
 */
class FxRateCalculator private constructor() {
    companion object {
        /**
         * For the supplied Pairs, compute the cross rates using the supplied rate table data.
         * Returns one rate for every requested CurrencyPair. The CurrencyPair serves as the callers
         * index to the result set.
         *
         * @param asAt    Requested date
         * @param currencyPairs   ISO currency codes for which to compute rates, i.e. NZD,AUD
         * @param rateMap Base->Target  (by default, USD->ISO)
         * @return rates for the requested pairs on the requested date.
         */
        fun compute(
            asAt: String = DateUtils.today,
            currencyPairs: Collection<IsoCurrencyPair>,
            rateMap: Map<String, FxRate>,
        ): FxPairResults {
            val rates: MutableMap<IsoCurrencyPair, FxRate> = HashMap()

            for (pair in currencyPairs) { // For all requested pairings
                if (!pair.from.equals(pair.to, ignoreCase = true)) { // Is the answer one?
                    val from = rateMap[pair.from.uppercase(Locale.getDefault())]!!
                    val to = rateMap[pair.to.uppercase(Locale.getDefault())]!!
                    val rate = if (pair.from == "USD") {
                        to.rate
                    } else {
                        to.rate.divide(from.rate, 8, RoundingMode.HALF_UP) // .divide(from.rate, 8, RoundingMode.HALF_UP)
                    }
                    rates[pair] = FxRate(from.to, to.to, rate, from.date)
                } else {
                    rates[pair] = FxRate(
                        (rateMap[pair.from] ?: error("")).from,
                        (rateMap[pair.to] ?: error("")).to,
                        BigDecimal.ONE,
                        asAt,
                    )
                }
            }
            return FxPairResults(rates)
        }
    }
}
