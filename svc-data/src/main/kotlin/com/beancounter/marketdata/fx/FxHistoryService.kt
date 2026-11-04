package com.beancounter.marketdata.fx

import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.currency.CurrencyService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Historical FX rates for a currency pair, read from the database cache. Used for
 * charting rate history and backfilling a currency pair's prices.
 *
 * Rates are stored as USD->X in the database, so a pair is triangulated:
 * - USD->X: Direct lookup
 * - X->USD: Lookup USD->X and invert (1/rate)
 * - X->Y: Lookup USD->X and USD->Y, calculate Y/X for each date
 */
@Service
class FxHistoryService(
    private val currencyService: CurrencyService,
    private val fxRateRepository: FxRateRepository
) {
    private val log = LoggerFactory.getLogger(FxHistoryService::class.java)
    private val dateUtils = DateUtils()

    /**
     * The last [months] of rates, up to today.
     */
    fun getHistoricalRates(
        fromCode: String,
        toCode: String,
        months: Int
    ): FxHistoryResponse {
        val endDate = dateUtils.date
        return getHistoricalRates(fromCode, toCode, endDate.minusMonths(months.toLong()), endDate)
    }

    /**
     * [getHistoricalRates] over an explicit window, [startDate] to [endDate] inclusive.
     */
    fun getHistoricalRates(
        fromCode: String,
        toCode: String,
        startDate: LocalDate,
        endDate: LocalDate = dateUtils.date
    ): FxHistoryResponse {
        val fromCurrency = currencyService.getCode(fromCode)
        val toCurrency = currencyService.getCode(toCode)
        val baseCurrency = currencyService.currencyConfig.baseCurrency
        val window = startDate..endDate
        val data =
            when (baseCurrency.code) {
                fromCurrency.code -> {
                    direct(ratesFromBase(baseCurrency, toCurrency, window))
                }
                toCurrency.code -> {
                    inverted(ratesFromBase(baseCurrency, fromCurrency, window))
                }
                else -> {
                    crossed(
                        ratesFromBase(baseCurrency, fromCurrency, window),
                        ratesFromBase(baseCurrency, toCurrency, window)
                    )
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

    // USD -> X: the stored rates as they are.
    private fun direct(rates: List<FxRate>): List<FxHistoryPoint> =
        rates.map { FxHistoryPoint(date = it.date, rate = it.rate) }

    // X -> USD: the inverse of the stored USD -> X rates.
    private fun inverted(rates: List<FxRate>): List<FxHistoryPoint> =
        rates.map { rate ->
            FxHistoryPoint(
                date = rate.date,
                rate = BigDecimal.ONE.divide(rate.rate, MathContext(RATE_PRECISION, RoundingMode.HALF_UP))
            )
        }

    // X -> Y: USD -> Y / USD -> X, on the dates both legs have a rate.
    private fun crossed(
        fromRates: List<FxRate>,
        toRates: List<FxRate>
    ): List<FxHistoryPoint> {
        val toRatesByDate = toRates.associateBy { it.date }
        return fromRates.mapNotNull { fromRate ->
            toRatesByDate[fromRate.date]?.let { toRate ->
                FxHistoryPoint(
                    date = fromRate.date,
                    rate = toRate.rate.divide(fromRate.rate, MathContext(RATE_PRECISION, RoundingMode.HALF_UP))
                )
            }
        }
    }

    /**
     * Stored rates from the base currency, without zero rates. A zero rate is bad data: it
     * cannot be inverted or crossed, and one bad row must not fail a whole history.
     */
    private fun ratesFromBase(
        base: Currency,
        to: Currency,
        window: ClosedRange<LocalDate>
    ): List<FxRate> {
        val (usable, zero) =
            fxRateRepository
                .findHistoricalRatesFromBase(base, to, window.start, window.endInclusive)
                .partition { it.rate.signum() != 0 }
        if (zero.isNotEmpty()) {
            log.warn("Skipped {} zero {}/{} rate(s): {}", zero.size, base.code, to.code, zero.map { it.date })
        }
        return usable
    }

    private companion object {
        const val RATE_PRECISION = 10
    }
}