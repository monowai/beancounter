package com.beancounter.client.ingest

import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Currency
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.NumberUtils
import java.math.BigDecimal
import java.time.LocalDate

private val numberUtils = NumberUtils()

/**
 * The pair to request when converting `from` to `to`, or null when the rate is already
 * set or both sides share a currency.
 */
internal fun currencyPair(
    from: Currency,
    to: Currency,
    rate: BigDecimal?
): IsoCurrencyPair? =
    if (numberUtils.isUnset(rate) && from.code != to.code) {
        IsoCurrencyPair(from = from.code, to = to.code)
    } else {
        null
    }

/**
 * A trade's three FX rates on its trade date: trade currency to portfolio, to base and to
 * cash currency. TrnInput and Trn carry them with different currency types; this view reads
 * and writes either, so the request, the skip rules and the resolution exist once.
 */
internal abstract class TradeRates {
    abstract val trnType: TrnType
    abstract val tradeDate: LocalDate
    abstract val tradeCurrency: Currency
    abstract val cashCurrency: Currency?
    abstract var portfolioRate: BigDecimal
    abstract var baseRate: BigDecimal
    abstract var cashRate: BigDecimal

    /**
     * A SPLIT moves no money. A forward-dated trade (e.g. a DIVI pay date 18 days out) waits:
     * FX providers reject future dates, so settle resolves the rates once the date arrives.
     */
    fun needsRates(today: LocalDate): Boolean =
        (
            numberUtils.isUnset(portfolioRate) ||
                numberUtils.isUnset(baseRate) ||
                numberUtils.isUnset(cashRate)
        ) &&
            trnType != TrnType.SPLIT &&
            !tradeDate.isAfter(today)

    fun fxRequest(portfolio: Portfolio): FxRequest {
        val fxRequest = FxRequest(tradeDate.toString(), mutableSetOf())
        fxRequest.addTradePf(currencyPair(tradeCurrency, portfolio.currency, portfolioRate))
        fxRequest.addTradeBase(currencyPair(tradeCurrency, portfolio.base, baseRate))
        cashCurrency?.let { cash ->
            fxRequest.addTradeCash(currencyPair(tradeCurrency, cash, cashRate))
        }
        return fxRequest
    }

    /**
     * Resolves all three rates before assigning any, so a missing rate throws without
     * leaving a managed Trn partially mutated in the JPA persistence context.
     */
    fun apply(
        rates: FxPairResults,
        fxRequest: FxRequest
    ) {
        val pf = resolve(fxRequest.tradePf, portfolioRate, rates)
        val base = resolve(fxRequest.tradeBase, baseRate, rates)
        val cash = resolve(fxRequest.tradeCash, cashRate, rates)
        portfolioRate = pf
        baseRate = base
        cashRate = cash
    }

    private fun resolve(
        pair: IsoCurrencyPair?,
        current: BigDecimal,
        rates: FxPairResults
    ): BigDecimal {
        if (pair != null && numberUtils.isUnset(current)) {
            return rates.rates[pair]?.rate
                ?: throw BusinessException("Missing FX rate for $pair")
        }
        return if (numberUtils.isUnset(current)) BigDecimal.ONE else current
    }
}

internal class TrnInputRates(
    private val trnInput: TrnInput
) : TradeRates() {
    override val trnType get() = trnInput.trnType
    override val tradeDate: LocalDate get() = trnInput.tradeDate
    override val tradeCurrency get() = Currency(trnInput.tradeCurrency)
    override val cashCurrency get() = trnInput.cashCurrency?.takeIf { it.isNotEmpty() }?.let { Currency(it) }
    override var portfolioRate: BigDecimal by trnInput::tradePortfolioRate
    override var baseRate: BigDecimal by trnInput::tradeBaseRate
    override var cashRate: BigDecimal by trnInput::tradeCashRate
}

internal class TrnRates(
    private val trn: Trn
) : TradeRates() {
    override val trnType get() = trn.trnType
    override val tradeDate: LocalDate get() = trn.tradeDate
    override val tradeCurrency get() = trn.tradeCurrency
    override val cashCurrency get() = trn.cashCurrency
    override var portfolioRate: BigDecimal by trn::tradePortfolioRate
    override var baseRate: BigDecimal by trn::tradeBaseRate
    override var cashRate: BigDecimal by trn::tradeCashRate
}