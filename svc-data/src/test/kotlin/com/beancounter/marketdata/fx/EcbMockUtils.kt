package com.beancounter.marketdata.fx

import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.AUD
import com.beancounter.marketdata.Constants.Companion.EUR
import com.beancounter.marketdata.Constants.Companion.GBP
import com.beancounter.marketdata.Constants.Companion.MYR
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.SGD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.fx.fxrates.ExRatesResponse
import java.math.BigDecimal

/**
 * Helper to create payloads for FX api.
 */
object EcbMockUtils {
    private val dateUtils = DateUtils()

    @JvmStatic
    operator fun get(
        date: String,
        rates: Map<String, BigDecimal>,
    ): ExRatesResponse = ExRatesResponse(USD.code, dateUtils.getFormattedDate(date), rates)

    @JvmStatic
    fun getRateMap(
        eur: String,
        sgd: String,
        gbp: String,
        nzd: String,
        aud: String,
        myr: String,
    ): Map<String, BigDecimal> =
        mapOf(
            AUD.code to BigDecimal(aud),
            EUR.code to BigDecimal(eur),
            GBP.code to BigDecimal(gbp),
            MYR.code to BigDecimal(myr),
            NZD.code to BigDecimal(nzd),
            SGD.code to BigDecimal(sgd),
        )
}
