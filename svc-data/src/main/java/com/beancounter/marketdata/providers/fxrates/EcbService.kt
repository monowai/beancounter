package com.beancounter.marketdata.providers.fxrates

import com.beancounter.common.model.FxRate
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.currency.CurrencyService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.*

@Service
class EcbService @Autowired internal constructor(private val fxGateway: FxGateway, private val currencyService: CurrencyService) {
    private val ecbDate = EcbDate()
    private val dateUtils = DateUtils()
    fun getRates(asAt: String): Collection<FxRate> {
        val ecbRates = fxGateway.getRatesForSymbols(
                ecbDate.getValidDate(asAt),
                currencyService.baseCurrency!!.code,
                currencies)
        val results: MutableCollection<FxRate> = ArrayList()
        if (ecbRates?.rates != null) {
            for (code in ecbRates.rates.keys) {
                results.add(
                        FxRate(currencyService.baseCurrency!!,
                                currencyService.getCode(code)!!,
                                ecbRates.rates[code] ?: error("No rate"),
                                dateUtils.getDateString(ecbRates.date))
                )
            }
        }
        return results
    }

    private val currencies: String?
        get() {
            val values = currencyService.currencies
            var result: java.lang.StringBuilder? = null
            for ((code) in values) {
                if (result == null) {
                    result = Optional.ofNullable(code).map { str: String? -> StringBuilder(str) }.orElse(null)
                } else {
                    result.append(",").append(code)
                }
            }
            return result?.toString()
        }

}