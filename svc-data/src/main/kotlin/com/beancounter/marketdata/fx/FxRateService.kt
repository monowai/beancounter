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
import com.beancounter.marketdata.fx.fxrates.FxProviderService
import com.beancounter.marketdata.markets.MarketService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Server side implementation to obtain FXRates from a source that can be
 * used to multiply by an amount to obtain the value in another currency.
 */
@Service
class FxRateService(
    private val fxProviderService: FxProviderService,
    private val currencyService: CurrencyService,
    private val marketService: MarketService,
    val marketUtils: PreviousClosePriceDate = PreviousClosePriceDate(DateUtils()),
    val fxRateRepository: FxRateRepository
) : FxService {
    private val log = LoggerFactory.getLogger(FxRateService::class.java)
    private val dateUtils = DateUtils()

    /**
     * Get base currency rate (e.g., USD->USD = 1.0).
     * When provider is null, returns any cached base rate.
     * When provider is specified, returns that provider's base rate.
     */
    private fun baseCurrencyFxRate(provider: String?): FxRate {
        val baseCurrency = currencyService.currencyConfig.baseCurrency
        val existingRate =
            if (provider != null) {
                fxRateRepository.findBaseRateByProvider(baseCurrency, provider)
            } else {
                fxRateRepository.findBaseRate(baseCurrency)
            }
        if (existingRate == null) {
            // Create base rate with the effective provider (default if null)
            val effectiveProvider = provider ?: fxProviderService.getDefaultProviderId()
            val usdToUsdRate =
                FxRate(
                    from = baseCurrency,
                    rate = BigDecimal.ONE,
                    date = dateUtils.getDate("1900-01-01"),
                    provider = effectiveProvider
                )
            fxRateRepository.save(usdToUsdRate)
            return usdToUsdRate
        }
        return existingRate
    }

    @Transactional
    override fun getRates(
        fxRequest: FxRequest,
        token: String
    ): FxResponse {
        for ((from, to) in fxRequest.pairs) {
            currencyService.getCode(from)
            currencyService.getCode(to)
        }

        val dateToFind = getDate(fxRequest.rateDate)
        val isComparisonMode = fxRequest.provider != null

        // For normal valuations (no explicit provider): use any cached rate regardless of provider
        // For comparison mode (explicit provider): use provider-specific cache
        var rates =
            if (isComparisonMode) {
                fxRateRepository.findByDateRangeAndProvider(dateToFind, fxRequest.provider!!)
            } else {
                fxRateRepository.findByDateRange(dateToFind)
            }

        if (rates.size <= 1) { // You should always get the base rate of 1
            // No cached rates found - fetch from external API using the calculated date
            val effectiveProvider = fxRequest.provider ?: fxProviderService.getDefaultProviderId()
            val dateString = dateToFind.toString()
            log.info(
                "Retrieving FX rates from {} for {} (requested={}, comparison={})",
                effectiveProvider,
                dateString,
                fxRequest.rateDate,
                isComparisonMode
            )
            rates = fxProviderService.getRates(dateString, fxRequest.provider).toMutableList()
            if (rates.isNotEmpty()) {
                fxRateRepository.saveAll(rates)
            }
            // Add in the base rate of 1 for USD
            rates.add(baseCurrencyFxRate(fxRequest.provider))
        }

        if (rates.isEmpty()) {
            val effectiveProvider = fxRequest.provider ?: fxProviderService.getDefaultProviderId()
            throw SystemException(
                "No rates found from $effectiveProvider for $dateToFind (requested=${fxRequest.rateDate})"
            )
        }

        val mappedRates = rates.associateBy { it.to.code }.toMutableMap()
        return FxResponse(
            FxRateCalculator.compute(
                fxRequest.rateDate,
                fxRequest.pairs,
                mappedRates
            )
        )
    }

    /**
     * Get available FX provider IDs.
     */
    fun getAvailableProviders(): List<String> = fxProviderService.getAvailableProviders()

    /**
     * Get historical FX rates for a currency pair from the database cache.
     * Used for charting rate history.
     *
     * Rates are stored as USD->X in the database. This method handles triangulation:
     * - USD->X: Direct lookup
     * - X->USD: Lookup USD->X and invert (1/rate)
     * - X->Y: Lookup USD->X and USD->Y, calculate Y/X for each date
     */
    fun getHistoricalRates(
        fromCode: String,
        toCode: String,
        months: Int
    ): FxHistoryResponse {
        val fromCurrency = currencyService.getCode(fromCode)
        val toCurrency = currencyService.getCode(toCode)
        val baseCurrency = currencyService.currencyConfig.baseCurrency

        val endDate = dateUtils.date
        val startDate = endDate.minusMonths(months.toLong())

        val data =
            when {
                // USD -> X: Direct lookup
                fromCurrency.code == baseCurrency.code -> {
                    val rates =
                        fxRateRepository.findHistoricalRatesFromBase(
                            baseCurrency,
                            toCurrency,
                            startDate,
                            endDate
                        )
                    rates.map { FxHistoryPoint(date = it.date, rate = it.rate) }
                }
                // X -> USD: Invert USD -> X
                toCurrency.code == baseCurrency.code -> {
                    val rates =
                        fxRateRepository.findHistoricalRatesFromBase(
                            baseCurrency,
                            fromCurrency,
                            startDate,
                            endDate
                        )
                    rates.map { rate ->
                        FxHistoryPoint(
                            date = rate.date,
                            rate = BigDecimal.ONE.divide(rate.rate, MathContext(10, RoundingMode.HALF_UP))
                        )
                    }
                }
                // X -> Y: Triangulate via USD (calculate USD->Y / USD->X)
                else -> {
                    val fromRates =
                        fxRateRepository.findHistoricalRatesFromBase(
                            baseCurrency,
                            fromCurrency,
                            startDate,
                            endDate
                        )
                    val toRates =
                        fxRateRepository.findHistoricalRatesFromBase(
                            baseCurrency,
                            toCurrency,
                            startDate,
                            endDate
                        )

                    // Index toRates by date for efficient lookup
                    val toRatesByDate = toRates.associateBy { it.date }

                    fromRates.mapNotNull { fromRate ->
                        toRatesByDate[fromRate.date]?.let { toRate ->
                            FxHistoryPoint(
                                date = fromRate.date,
                                rate = toRate.rate.divide(fromRate.rate, MathContext(10, RoundingMode.HALF_UP))
                            )
                        }
                    }
                }
            }

        return FxHistoryResponse(
            from = fromCode,
            to = toCode,
            startDate = startDate,
            endDate = endDate,
            data = data
        )
    }

    fun getDate(date: String) =
        marketUtils.getPriceDate(
            dateUtils.offsetNow(date).toZonedDateTime(),
            marketService.getMarket("US"),
            dateUtils.isToday(date)
        )
}