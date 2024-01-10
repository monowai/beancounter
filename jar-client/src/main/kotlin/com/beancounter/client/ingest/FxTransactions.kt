package com.beancounter.client.ingest

import com.beancounter.client.FxService
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Currency
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.NumberUtils
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Client side service to obtain FX rates in response to Transaction requests.
 */
@Service
class FxTransactions(
    private val fxClientService: FxService,
) {
    private val numberUtils = NumberUtils()

    fun getFxRequest(
        portfolio: Portfolio,
        trn: TrnInput,
    ): FxRequest {
        val tradeDate = trn.tradeDate.toString()
        val fxRequest = getFxRequest(HashMap(), tradeDate)
        fxRequest.addTradePf(
            pair(Currency(trn.tradeCurrency), portfolio.currency, trn.tradePortfolioRate),
        )
        fxRequest.addTradeBase(
            pair(Currency(trn.tradeCurrency), portfolio.base, trn.tradeBaseRate),
        )
        if (trn.cashCurrency != null && trn.cashCurrency != "") {
            fxRequest.addTradeCash(
                pair(Currency(trn.tradeCurrency), Currency(trn.cashCurrency!!), trn.tradeCashRate),
            )
        }
        return fxRequest
    }

    fun setRates(
        rates: FxPairResults,
        fxRequest: FxRequest,
        trnInput: TrnInput,
    ) {
        if (fxRequest.tradePf != null && numberUtils.isUnset(trnInput.tradePortfolioRate)) {
            trnInput.tradePortfolioRate = rates.rates[fxRequest.tradePf!!]!!.rate
        } else {
            if (numberUtils.isUnset(trnInput.tradePortfolioRate)) {
                trnInput.tradePortfolioRate = BigDecimal.ONE
            }
        }
        if (fxRequest.tradeBase != null && numberUtils.isUnset(trnInput.tradeBaseRate)) {
            trnInput.tradeBaseRate = rates.rates[fxRequest.tradeBase!!]!!.rate
        } else {
            if (numberUtils.isUnset(trnInput.tradeBaseRate)) {
                trnInput.tradeBaseRate = BigDecimal.ONE
            }
        }
        if (fxRequest.tradeCash != null && numberUtils.isUnset(trnInput.tradeCashRate)) {
            trnInput.tradeCashRate = rates.rates[fxRequest.tradeCash!!]!!.rate
        } else {
            if (numberUtils.isUnset(trnInput.tradeCashRate)) {
                trnInput.tradeCashRate = BigDecimal.ONE
            }
        }
    }

    fun pair(
        from: Currency,
        to: Currency,
        rate: BigDecimal?,
    ): IsoCurrencyPair? {
        if (numberUtils.isUnset(rate) && from.code != to.code) {
            return IsoCurrencyPair(from = from.code, to = to.code)
        }
        return null
    }

    private fun getFxRequest(
        fxRequests: MutableMap<String?, FxRequest>,
        tradeDate: String,
    ): FxRequest {
        val fxRequest = FxRequest(tradeDate, ArrayList())
        fxRequests[tradeDate] = fxRequest
        return fxRequest
    }

    fun needsRates(trnInput: TrnInput): Boolean {
        return (
            numberUtils.isUnset(trnInput.tradePortfolioRate) ||
                numberUtils.isUnset(trnInput.tradeBaseRate) ||
                numberUtils.isUnset(trnInput.tradeCashRate)
        ) && trnInput.trnType != TrnType.SPLIT
    }

    fun setRates(
        portfolio: Portfolio,
        trnInput: TrnInput,
    ) {
        if (needsRates(trnInput)) {
            val fxRequest = getFxRequest(portfolio, trnInput)
            val (data) = fxClientService.getRates(fxRequest)
            setRates(data, fxRequest, trnInput)
        }
    }
}
