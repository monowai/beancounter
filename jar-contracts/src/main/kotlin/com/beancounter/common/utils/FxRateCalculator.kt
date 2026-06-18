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
        private val dateUtils = DateUtils()

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
            asAt: String = DateUtils.TODAY,
            currencyPairs: Collection<IsoCurrencyPair>,
            rateMap: Map<String, FxRate>
        ): FxPairResults {
            val rates =
                currencyPairs.associateWith { pair ->
                    calculateRate(
                        pair,
                        rateMap,
                        asAt
                    )
                }
            return FxPairResults(rates)
        }

        private fun calculateRate(
            pair: IsoCurrencyPair,
            rateMap: Map<String, FxRate>,
            asAt: String
        ): FxRate {
            if (!pair.from.equals(
                    pair.to,
                    ignoreCase = true
                )
            ) {
                val fromRate =
                    getRate(
                        pair.from,
                        rateMap
                    )
                val toRate =
                    getRate(
                        pair.to,
                        rateMap
                    )
                val rate =
                    if (pair.from == "USD") {
                        toRate.rate
                    } else {
                        toRate.rate.divide(
                            fromRate.rate,
                            8,
                            RoundingMode.HALF_UP
                        )
                    }
                return FxRate(
                    fromRate.to,
                    toRate.to,
                    rate,
                    dateUtils.getDate(asAt)
                )
            } else {
                return FxRate(
                    rateMap[pair.from]?.from ?: throw IllegalArgumentException(
                        "Rate for ${pair.from} not found"
                    ),
                    rateMap[pair.to]?.to ?: throw IllegalArgumentException(
                        "Rate for ${pair.to} not found"
                    ),
                    BigDecimal.ONE,
                    dateUtils.getDate(asAt)
                )
            }
        }

        private fun getRate(
            currency: String,
            rateMap: Map<String, FxRate>
        ): FxRate =
            rateMap[currency.uppercase(Locale.getDefault())]
                ?: throw IllegalArgumentException("Rate for $currency not found")
    }
}