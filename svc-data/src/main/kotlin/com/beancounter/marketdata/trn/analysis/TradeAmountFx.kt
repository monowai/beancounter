package com.beancounter.marketdata.trn.analysis

import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Trn
import com.beancounter.marketdata.fx.FxRateService
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * The one FX rule for reporting trade amounts in another currency: prefer the rate
 * recorded on the trn at trade date, fall back to today's rate, never total unlike currencies.
 */
@Service
class TradeAmountFx(
    private val fxRateService: FxRateService
) {
    /**
     * A converter for [transactions] into [targetCurrency]. Today's rates are fetched once,
     * and only for trns without a usable recorded rate.
     */
    fun into(
        targetCurrency: String,
        transactions: Iterable<Trn>
    ): TradeAmountsIn {
        val currenciesNeeded =
            transactions
                .filter { recordedRate(it, targetCurrency) == null }
                .map { it.tradeCurrency.code }
                .filter { it != targetCurrency }
                .distinct()

        val todayRates =
            if (currenciesNeeded.isNotEmpty()) {
                val fxRequest = FxRequest(rateDate = "today")
                currenciesNeeded.forEach { fxRequest.add(IsoCurrencyPair(it, targetCurrency)) }
                val fxResponse = fxRateService.getRates(fxRequest, "")
                fxResponse.data.rates.entries.associate { (pair, fxRate) ->
                    "${pair.from}:${pair.to}" to fxRate.rate
                }
            } else {
                emptyMap()
            }
        return TradeAmountsIn(targetCurrency, todayRates)
    }
}

/**
 * Trade amounts expressed in [targetCurrency], using [todayRates] only where the trn
 * records no usable rate.
 */
class TradeAmountsIn(
    private val targetCurrency: String,
    private val todayRates: Map<String, BigDecimal>
) {
    /**
     * The trn's trade amount in the target currency, preferring the trade-date rate so past months
     * stay stable and agree with positions. Fails rather than total amounts in unlike currencies.
     */
    fun of(trn: Trn): BigDecimal {
        val fromCurrency = trn.tradeCurrency.code
        if (fromCurrency == targetCurrency) return trn.tradeAmount

        val rate =
            recordedRate(trn, targetCurrency)
                ?: todayRates["$fromCurrency:$targetCurrency"]
                ?: throw BusinessException("No FX rate from $fromCurrency to $targetCurrency")
        return trn.tradeAmount.multiply(rate)
    }
}

/**
 * The rate stamped on the trn at trade date, when [targetCurrency] is one it records.
 * A stamp of exactly 1 between different currencies means the rate was never computed.
 */
private fun recordedRate(
    trn: Trn,
    targetCurrency: String
): BigDecimal? {
    val rate =
        when (targetCurrency) {
            trn.portfolio.currency.code -> trn.tradePortfolioRate
            trn.portfolio.base.code -> trn.tradeBaseRate
            else -> null
        }
    return rate?.takeUnless { it.compareTo(BigDecimal.ONE) == 0 }
}