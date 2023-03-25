package com.beancounter.marketdata.fx.fxrates

import com.beancounter.common.model.FxRate
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.currency.CurrencyService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * exchangeratesapi.io service implementation to obtain fx rates.  T
 */
@Service
class EcbService @Autowired internal constructor(
    private val fxGateway: FxGateway,
    private val currencyService: CurrencyService,
    dateUtils: DateUtils,
) {
    private val ecbDate = EcbDate(dateUtils)
    fun getRates(asAt: String): Collection<FxRate> {
        val ecbRates = fxGateway.getRatesForSymbols(
            ecbDate.getValidDate(asAt),
            currencyService.baseCurrency.code,
            currencyService.currenciesAs,
        )
        val results: MutableCollection<FxRate> = ArrayList()
        if (ecbRates?.rates != null) {
            for (code in ecbRates.rates.keys) {
                results.add(
                    FxRate(
                        currencyService.baseCurrency,
                        currencyService.getCode(code),
                        ecbRates.rates[code] ?: error("No rate"),
                        ecbRates.date.toString(),
                    ),
                )
            }
        }
        return results
    }
}
