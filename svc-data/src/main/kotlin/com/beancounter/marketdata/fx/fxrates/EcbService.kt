package com.beancounter.marketdata.fx.fxrates

import com.beancounter.common.model.FxRate
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.currency.CurrencyService
import io.github.resilience4j.ratelimiter.annotation.RateLimiter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * exchangeratesapi.io service implementation to obtain fx rates.  T
 */
@Service
class EcbService
    @Autowired
    internal constructor(
        private val fxGateway: FxGateway,
        private val currencyService: CurrencyService,
        dateUtils: DateUtils
    ) {
        private val ecbDate = EcbDate(dateUtils)

        @RateLimiter(name = "fxRates")
        fun getRates(asAt: String): List<FxRate> {
            val ecbRates =
                fxGateway.getRatesForSymbols(
                    ecbDate.getValidDate(asAt),
                    currencyService.currencyConfig.baseCurrency.code,
                    currencyService.currenciesAs()
                )
            val results: MutableList<FxRate> = ArrayList()
            if (ecbRates?.rates != null) {
                for (code in ecbRates.rates.keys) {
                    results.add(
                        FxRate(
                            currencyService.currencyConfig.baseCurrency,
                            currencyService.getCode(code),
                            ecbRates.rates[code] ?: error("No rate"),
                            ecbDate.dateUtils.getDate(ecbRates.date.toString())
                        )
                    )
                }
            }
            return results
        }
    }