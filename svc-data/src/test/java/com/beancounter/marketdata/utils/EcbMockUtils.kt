package com.beancounter.marketdata.utils

import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.providers.fxrates.EcbRates
import java.math.BigDecimal
import java.util.*

object EcbMockUtils {
    private val dateUtils = DateUtils()
    @JvmStatic
    operator fun get(date: String, rates: Map<String, BigDecimal>): EcbRates {
        return EcbRates("USD", dateUtils.getDate(date)!!, rates)
    }

    @JvmStatic
    fun getRateMap(
            eur: String?,
            sgd: String?,
            gbp: String?,
            nzd: String?,
            aud: String?
    ): Map<String, BigDecimal> {
        val ratesTest: MutableMap<String, BigDecimal> = TreeMap()
        ratesTest["AUD"] = BigDecimal(aud)
        ratesTest["EUR"] = BigDecimal(eur)
        ratesTest["GBP"] = BigDecimal(gbp)
        ratesTest["NZD"] = BigDecimal(nzd)
        ratesTest["SGD"] = BigDecimal(sgd)
        ratesTest["USD"] = BigDecimal("1.0")
        return ratesTest
    }
}