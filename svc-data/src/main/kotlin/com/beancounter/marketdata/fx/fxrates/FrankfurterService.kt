package com.beancounter.marketdata.fx.fxrates

import com.beancounter.common.model.FxRate
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.currency.CurrencyService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * Frankfurter (frankfurter.dev) FX rate provider - free, unlimited, ECB-based rates.
 * Primary FX rate provider.
 */
@Service
class FrankfurterService
    @Autowired
    internal constructor(
        private val frankfurterGateway: FrankfurterGateway,
        private val currencyService: CurrencyService,
        dateUtils: DateUtils
    ) : FxRateProvider {
        private val log = LoggerFactory.getLogger(FrankfurterService::class.java)
        private val ecbDate = EcbDate(dateUtils)

        override val id: String = "FRANKFURTER"

        override fun getRates(asAt: String): List<FxRate> =
            try {
                val validDate = ecbDate.getValidDate(asAt)
                val response =
                    frankfurterGateway.getRatesForSymbols(
                        validDate,
                        currencyService.currencyConfig.baseCurrency.code,
                        currencyService.currenciesAs()
                    )
                if (response?.rates != null) {
                    response.rates.map { (code, rate) ->
                        FxRate(
                            from = currencyService.currencyConfig.baseCurrency,
                            to = currencyService.getCode(code),
                            rate = rate,
                            date = ecbDate.dateUtils.getDate(response.date.toString()),
                            provider = id
                        )
                    }
                } else {
                    log.warn("Frankfurter returned null response for date {}", asAt)
                    emptyList()
                }
            } catch (e: Exception) {
                log.warn("Frankfurter FX rate fetch failed for {}: {}", asAt, e.message)
                emptyList()
            }
    }