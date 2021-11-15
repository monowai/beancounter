package com.beancounter.marketdata.fx

import com.beancounter.client.FxService
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.utils.RateCalculator
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.fx.fxrates.EcbService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

/**
 * Server side implementation to obtain FXRates from a source.
 */
@Service
class FxRateService
@Autowired internal constructor(private val ecbService: EcbService, private val currencyService: CurrencyService) :
    FxService {

    @Cacheable("fx.rates")
    override fun getRates(fxRequest: FxRequest): FxResponse {
        verify(fxRequest.pairs)
        val rates: Collection<FxRate>
        val rateDate = fxRequest.rateDate
        rates = ecbService.getRates(rateDate)
        val mappedRates: MutableMap<String, FxRate> = HashMap()
        for (rate in rates) {
            mappedRates[rate.to.code] = rate
        }
        return FxResponse(RateCalculator.compute(rateDate, fxRequest.pairs, mappedRates))
    }

    private fun verify(isoCurrencyPairs: Collection<IsoCurrencyPair>) {
        val invalid: MutableCollection<String> = ArrayList()
        for ((from, to) in isoCurrencyPairs) {
            if (currencyService.getCode(from) == null) {
                invalid.add(from)
            }
            if (currencyService.getCode(to) == null) {
                invalid.add(to)
            }
        }
        if (!invalid.isEmpty()) {
            throw BusinessException(
                String.format(
                    "Unsupported currencies in the request %s",
                    java.lang.String.join(",", invalid)
                )
            )
        }
    }
}
