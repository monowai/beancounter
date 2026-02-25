package com.beancounter.marketdata.fx.fxrates

import com.beancounter.common.model.FxRate
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.currency.CurrencyService
import io.github.resilience4j.ratelimiter.annotation.RateLimiter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException

/**
 * exchangeratesapi.io service implementation to obtain FX rates.
 * Used as fallback when Frankfurter is unavailable.
 */
@Service
class EcbService
    @Autowired
    internal constructor(
        private val fxGateway: FxGateway,
        private val currencyService: CurrencyService,
        dateUtils: DateUtils
    ) : FxRateProvider {
        private val log = LoggerFactory.getLogger(EcbService::class.java)
        private val ecbDate = EcbDate(dateUtils)

        override val id: String = "EXCHANGE_RATES_API"

        @RateLimiter(name = "fxRates")
        override fun getRates(asAt: String): List<FxRate> =
            try {
                val validDate = ecbDate.getValidDate(asAt)
                val baseCurrency = currencyService.currencyConfig.baseCurrency.code
                val symbols = currencyService.currenciesAs()
                log.info("Fetching FX rates: date={}, base={}, symbols={}", validDate, baseCurrency, symbols)
                val ecbRates = fxGateway.getRatesForSymbols(validDate, baseCurrency, symbols)
                if (ecbRates?.rates != null) {
                    log.info("Received {} rates from ExchangeRatesAPI for {}", ecbRates.rates.size, validDate)
                    ecbRates.rates.map { (code, rate) ->
                        FxRate(
                            from = currencyService.currencyConfig.baseCurrency,
                            to = currencyService.getCode(code),
                            rate = rate,
                            date = ecbDate.dateUtils.getDate(ecbRates.date.toString()),
                            provider = id
                        )
                    }
                } else {
                    log.warn("ExchangeRatesAPI returned null response for date {}", asAt)
                    emptyList()
                }
            } catch (e: RestClientException) {
                log.error("ExchangeRatesAPI FX rate fetch failed for {}: {}", asAt, e.message, e)
                emptyList()
            }
    }