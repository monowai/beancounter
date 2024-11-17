package com.beancounter.marketdata.fx

import com.beancounter.client.FxService
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.exception.SystemException
import com.beancounter.common.model.FxRate
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.FxRateCalculator
import com.beancounter.common.utils.PreviousClosePriceDate
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.fx.fxrates.EcbService
import com.beancounter.marketdata.markets.MarketService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Server side implementation to obtain FXRates from a source that can be
 * used to multiply by an amount to obtain the value in another currency.
 */
@Service
class FxRateService(
    private val ecbService: EcbService,
    private val currencyService: CurrencyService,
    private val marketService: MarketService,
    val marketUtils: PreviousClosePriceDate = PreviousClosePriceDate(DateUtils()),
    val fxRateRepository: FxRateRepository,
) : FxService {
    private fun baseCurrencyFxRate(): FxRate {
        val existingRate = fxRateRepository.findBaseRate(currencyService.baseCurrency)
        if (existingRate == null) {
            val usdToUsdRate =
                FxRate(
                    from = currencyService.baseCurrency,
                    rate = BigDecimal.ONE,
                    date = dateUtils.getDate("1900-01-01"),
                )
            fxRateRepository.save(usdToUsdRate)
            return usdToUsdRate
        }
        return existingRate
    }

    override fun getRates(
        fxRequest: FxRequest,
        token: String,
    ): FxResponse {
        for ((from, to) in fxRequest.pairs) {
            currencyService.getCode(from)
            currencyService.getCode(to)
        }
        val dateToFind = getDate(fxRequest.rateDate)
        var rates = fxRateRepository.findByDateRange(dateToFind)
        if (rates.size <= 1) { // You should always get the base rate of 1
            LoggerFactory
                .getLogger(FxRateService::class.java)
                .info("Retrieving ECB rates $dateToFind/${fxRequest.rateDate}")
            rates = ecbService.getRates(fxRequest.rateDate).toMutableList()
            fxRateRepository.saveAll(rates)
            // Add in the base rate of 1 for USD
            rates.add(baseCurrencyFxRate())
        }
        if (rates.isEmpty()) {
            throw SystemException("No rates found for ${fxRequest.rateDate}")
        }
        val mappedRates = rates.associateBy { it.to.code }.toMutableMap()

        return FxResponse(FxRateCalculator.compute(fxRequest.rateDate, fxRequest.pairs, mappedRates))
    }

    private val dateUtils = DateUtils()

    fun getDate(date: String) =
        marketUtils.getPriceDate(
            dateUtils.offsetNow(date).toZonedDateTime(),
            marketService.getMarket("US"),
            dateUtils.isToday(date),
        )
}
