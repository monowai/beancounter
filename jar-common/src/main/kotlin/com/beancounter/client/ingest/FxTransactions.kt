package com.beancounter.client.ingest

import com.beancounter.client.FxService
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Currency
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.NumberUtils
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Client side service to obtain FX rates in response to Transaction requests.
 */
@Service
class FxTransactions(
    private val fxClientService: FxService,
    private val dateUtils: DateUtils = DateUtils()
) {
    private val numberUtils = NumberUtils()

    fun getFxRequest(
        portfolio: Portfolio,
        trn: TrnInput
    ): FxRequest {
        val tradeDate = trn.tradeDate.toString()
        val fxRequest = getFxRequest(HashMap(), tradeDate)
        fxRequest.addTradePf(
            pair(Currency(trn.tradeCurrency), portfolio.currency, trn.tradePortfolioRate)
        )
        fxRequest.addTradeBase(
            pair(Currency(trn.tradeCurrency), portfolio.base, trn.tradeBaseRate)
        )
        if (trn.cashCurrency != null && trn.cashCurrency != "") {
            fxRequest.addTradeCash(
                pair(Currency(trn.tradeCurrency), Currency(trn.cashCurrency!!), trn.tradeCashRate)
            )
        }
        return fxRequest
    }

    fun setRates(
        rates: FxPairResults,
        fxRequest: FxRequest,
        trnInput: TrnInput
    ) {
        if (fxRequest.tradePf != null && numberUtils.isUnset(trnInput.tradePortfolioRate)) {
            trnInput.tradePortfolioRate =
                (
                    rates.rates[fxRequest.tradePf]
                        ?: throw BusinessException("Missing FX rate for ${fxRequest.tradePf}")
                ).rate
        } else {
            if (numberUtils.isUnset(trnInput.tradePortfolioRate)) {
                trnInput.tradePortfolioRate = BigDecimal.ONE
            }
        }
        if (fxRequest.tradeBase != null && numberUtils.isUnset(trnInput.tradeBaseRate)) {
            trnInput.tradeBaseRate =
                (
                    rates.rates[fxRequest.tradeBase]
                        ?: throw BusinessException("Missing FX rate for ${fxRequest.tradeBase}")
                ).rate
        } else {
            if (numberUtils.isUnset(trnInput.tradeBaseRate)) {
                trnInput.tradeBaseRate = BigDecimal.ONE
            }
        }
        if (fxRequest.tradeCash != null && numberUtils.isUnset(trnInput.tradeCashRate)) {
            trnInput.tradeCashRate =
                (
                    rates.rates[fxRequest.tradeCash]
                        ?: throw BusinessException("Missing FX rate for ${fxRequest.tradeCash}")
                ).rate
        } else {
            if (numberUtils.isUnset(trnInput.tradeCashRate)) {
                trnInput.tradeCashRate = BigDecimal.ONE
            }
        }
    }

    fun pair(
        from: Currency,
        to: Currency,
        rate: BigDecimal?
    ): IsoCurrencyPair? {
        if (numberUtils.isUnset(rate) && from.code != to.code) {
            return IsoCurrencyPair(from = from.code, to = to.code)
        }
        return null
    }

    private fun getFxRequest(
        fxRequests: MutableMap<String?, FxRequest>,
        tradeDate: String
    ): FxRequest {
        val fxRequest = FxRequest(tradeDate, mutableSetOf())
        fxRequests[tradeDate] = fxRequest
        return fxRequest
    }

    fun needsRates(trnInput: TrnInput): Boolean =
        (
            numberUtils.isUnset(trnInput.tradePortfolioRate) ||
                numberUtils.isUnset(trnInput.tradeBaseRate) ||
                numberUtils.isUnset(trnInput.tradeCashRate)
        ) &&
            trnInput.trnType != TrnType.SPLIT &&
            // Forward tradeDate (e.g. DIVI pay date 18 days out) — FX providers reject
            // future dates. Auto-settle resolves rates when tradeDate arrives.
            !trnInput.tradeDate.isAfter(dateUtils.date)

    fun setRates(
        portfolio: Portfolio,
        trnInput: TrnInput
    ) {
        if (needsRates(trnInput)) {
            val fxRequest = getFxRequest(portfolio, trnInput)
            val (data) = fxClientService.getRates(fxRequest)
            setRates(data, fxRequest, trnInput)
        }
    }

    /**
     * Settle-time FX resolution. PROPOSED corporate-event trns (DIVI) are written with
     * forward-dated tradeDate (= payDate) and their FX rates are deferred (see
     * `needsRates(TrnInput)`). When auto-settle (or a manual settle) flips PROPOSED → SETTLED
     * the tradeDate has now arrived, so we resolve and stamp the rates on the persisted Trn.
     */
    fun setRates(
        portfolio: Portfolio,
        trn: Trn
    ) {
        if (!needsRates(trn)) return
        val fxRequest = getFxRequest(portfolio, trn)
        val (data) = fxClientService.getRates(fxRequest)
        // Resolve all three rates into locals first. If the provider returns an
        // incomplete response, throw before touching the managed entity so the
        // Trn is not left partially mutated in the JPA persistence context.
        val pf = resolveRate(fxRequest.tradePf, trn.tradePortfolioRate, data)
        val base = resolveRate(fxRequest.tradeBase, trn.tradeBaseRate, data)
        val cash = resolveRate(fxRequest.tradeCash, trn.tradeCashRate, data)
        trn.tradePortfolioRate = pf
        trn.tradeBaseRate = base
        trn.tradeCashRate = cash
    }

    private fun resolveRate(
        pair: IsoCurrencyPair?,
        current: BigDecimal,
        data: FxPairResults
    ): BigDecimal {
        if (pair != null && numberUtils.isUnset(current)) {
            return data.rates[pair]?.rate
                ?: throw IllegalStateException("FX rate missing for $pair")
        }
        return if (numberUtils.isUnset(current)) BigDecimal.ONE else current
    }

    fun needsRates(trn: Trn): Boolean =
        (
            numberUtils.isUnset(trn.tradePortfolioRate) ||
                numberUtils.isUnset(trn.tradeBaseRate) ||
                numberUtils.isUnset(trn.tradeCashRate)
        ) &&
            trn.trnType != TrnType.SPLIT &&
            !trn.tradeDate.isAfter(dateUtils.date)

    private fun getFxRequest(
        portfolio: Portfolio,
        trn: Trn
    ): FxRequest {
        val tradeDate = trn.tradeDate.toString()
        val fxRequest = FxRequest(tradeDate, mutableSetOf())
        fxRequest.addTradePf(pair(trn.tradeCurrency, portfolio.currency, trn.tradePortfolioRate))
        fxRequest.addTradeBase(pair(trn.tradeCurrency, portfolio.base, trn.tradeBaseRate))
        trn.cashCurrency?.let { cash ->
            fxRequest.addTradeCash(pair(trn.tradeCurrency, cash, trn.tradeCashRate))
        }
        return fxRequest
    }
}