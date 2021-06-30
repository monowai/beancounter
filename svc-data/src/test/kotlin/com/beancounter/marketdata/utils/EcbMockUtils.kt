package com.beancounter.marketdata.utils

import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.AUD
import com.beancounter.marketdata.Constants.Companion.EUR
import com.beancounter.marketdata.Constants.Companion.GBP
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.SGD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.providers.fxrates.ExRatesResponse
import java.math.BigDecimal
import java.util.TreeMap

/**
 * Helper to create payloads for FX api.
 */
object EcbMockUtils {
    private val dateUtils = DateUtils()
    @JvmStatic
    operator fun get(date: String, rates: Map<String, BigDecimal>): ExRatesResponse {
        return ExRatesResponse(USD.code, dateUtils.getDate(date), rates)
    }

    @JvmStatic
    fun getRateMap(
        eur: String,
        sgd: String,
        gbp: String,
        nzd: String,
        aud: String
    ): Map<String, BigDecimal> {
        val ratesTest: MutableMap<String, BigDecimal> = TreeMap()
        ratesTest[AUD.code] = BigDecimal(aud)
        ratesTest[EUR.code] = BigDecimal(eur)
        ratesTest[GBP.code] = BigDecimal(gbp)
        ratesTest[NZD.code] = BigDecimal(nzd)
        ratesTest[SGD.code] = BigDecimal(sgd)
        ratesTest[USD.code] = BigDecimal("1.0")
        return ratesTest
    }
}
