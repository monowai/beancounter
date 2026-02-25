package com.beancounter.marketdata.fx

import com.beancounter.client.FxService
import com.beancounter.common.contracts.BulkFxRequest
import com.beancounter.common.contracts.BulkFxResponse
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.exception.SystemException
import com.beancounter.common.model.FxRate
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.FxRateCalculator
import com.beancounter.common.utils.PreviousClosePriceDate
import com.beancounter.marketdata.cache.CacheInvalidationProducer
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.fx.fxrates.FxProviderService
import com.beancounter.marketdata.markets.MarketService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

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
    private var cacheInvalidationProducer: CacheInvalidationProducer? = null

    @Autowired(required = false)
    fun setCacheInvalidationProducer(producer: CacheInvalidationProducer?) {
        this.cacheInvalidationProducer = producer
    }

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

        // Check if we have actual rates for the requested date (not just base rates from 1900)
        val hasRatesForDate = rates.any { it.date == dateToFind }
        if (!hasRatesForDate) {
            // No cached rates found for this date - fetch from external API
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
                cacheInvalidationProducer?.sendFxEvent(dateToFind)
                // Add in the base rate of 1 for USD
                rates.add(baseCurrencyFxRate(fxRequest.provider))
            }
        }

        if (rates.isEmpty()) {
            // Both providers failed — try most recent cached rates
            rates = fxRateRepository.findMostRecentBefore(dateToFind).toMutableList()
            if (rates.isNotEmpty()) {
                log.warn(
                    "Using cached rates from {} for {} (providers unavailable)",
                    rates.first().date,
                    dateToFind
                )
                rates.add(baseCurrencyFxRate(fxRequest.provider))
            } else {
                val effectiveProvider = fxRequest.provider ?: fxProviderService.getDefaultProviderId()
                throw SystemException(
                    "No rates found from $effectiveProvider for $dateToFind (requested=${fxRequest.rateDate})"
                )
            }
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
     * Get FX rates for multiple dates in a single call (DB-only, no provider calls).
     * Only dates that already have cached rates are returned; dates with no prior
     * cached data are skipped entirely. When a date within the range has no exact
     * match, `lastKnownRates` (the most recent prior cached date) provides the fallback.
     */
    @Transactional
    fun getBulkRates(request: BulkFxRequest): BulkFxResponse {
        if (request.pairs.isEmpty()) return BulkFxResponse()

        val startDate = dateUtils.getDate(request.startDate)
        val endDate = dateUtils.getDate(request.endDate)

        // Single query for all rates in the date range
        val allRates = fxRateRepository.findByDateBetween(startDate, endDate)

        // Also fetch the base currency rate (USD->USD = 1.0)
        val baseCurrency = currencyService.currencyConfig.baseCurrency
        val baseRate = fxRateRepository.findBaseRate(baseCurrency)

        // Group rates by date
        val ratesByDate = allRates.groupBy { it.date }

        // Collect all unique dates we need rates for
        val allDates = collectUniqueDates(startDate, endDate, ratesByDate.keys)

        val result = mutableMapOf<String, FxPairResults>()
        var lastKnownRates: Map<String, FxRate>? = null

        for (date in allDates) {
            val ratesForDate = ratesByDate[date]
            val mappedRates =
                if (ratesForDate != null) {
                    val mapped = ratesForDate.associateBy { it.to.code }.toMutableMap()
                    if (baseRate != null) mapped.putIfAbsent(baseCurrency.code, baseRate)
                    lastKnownRates = mapped
                    mapped
                } else {
                    // Fall back to nearest prior date
                    lastKnownRates ?: continue
                }

            try {
                result[date.toString()] =
                    FxRateCalculator.compute(
                        date.toString(),
                        request.pairs,
                        mappedRates
                    )
            } catch (e: IllegalArgumentException) {
                log.warn("Missing FX rate data for {} on {}: {}", request.pairs, date, e.message)
            }
        }

        return BulkFxResponse(result)
    }

    private fun collectUniqueDates(
        startDate: LocalDate,
        endDate: LocalDate,
        cachedDates: Set<LocalDate>
    ): List<LocalDate> {
        // Return all dates from the cached set within range, sorted
        return cachedDates
            .filter { !it.isBefore(startDate) && !it.isAfter(endDate) }
            .sorted()
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