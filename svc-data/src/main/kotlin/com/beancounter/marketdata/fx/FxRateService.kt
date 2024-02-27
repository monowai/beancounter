package com.beancounter.marketdata.fx

import com.beancounter.client.FxService
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.FxRate
import com.beancounter.common.utils.FxRateCalculator
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.fx.fxrates.EcbService
import io.github.resilience4j.ratelimiter.annotation.RateLimiter
import org.springframework.cache.annotation.Cacheable
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service

/**
 * Server side implementation to obtain FXRates from a source that can be
 * used to multiply by an amount to obtain the value in another currency.
 */
@Service
class FxRateService
(private val ecbService: EcbService, private val currencyService: CurrencyService) :
    FxService {
    @Cacheable("fx.rates")
    @Retryable
    @RateLimiter(name = "fxRates")
    override fun getRates(fxRequest: FxRequest): FxResponse {
        for ((from, to) in fxRequest.pairs) {
            currencyService.getCode(from)
            currencyService.getCode(to)
        }
        val rates: Collection<FxRate> = ecbService.getRates(fxRequest.rateDate)
        if (rates.isEmpty()) throw BusinessException("No Rates were returned for ${fxRequest.rateDate}")
        val mappedRates = rates.associateBy { it.to.code }
        return FxResponse(FxRateCalculator.compute(fxRequest.rateDate, fxRequest.pairs, mappedRates))
    }
}
