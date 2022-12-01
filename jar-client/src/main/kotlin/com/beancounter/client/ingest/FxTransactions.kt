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
    private val fxClientService: FxService
) {
    private val numberUtils = NumberUtils()

    fun getFxRequest(portfolio: Portfolio, trn: TrnInput): FxRequest {
        val fxRequestMap: MutableMap<String?, FxRequest> = HashMap()
        val tradeDate = trn.tradeDate.toString()
        val fxRequest = getFxRequest(fxRequestMap, tradeDate)
        fxRequest.addTradePf(
            pair(portfolio.currency, trn, trn.tradePortfolioRate)
        )
        fxRequest.addTradeBase(
            pair(portfolio.base, trn, trn.tradeBaseRate)
        )
        if (trn.cashCurrency != null && trn.cashCurrency != "") {
            fxRequest.addTradeCash(
                pair(Currency(trn.cashCurrency!!), trn, trn.tradeCashRate)
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

    fun pair(currency: Currency, trn: TrnInput, rate: BigDecimal?): IsoCurrencyPair? {
        val from = Currency(trn.tradeCurrency)
        if (numberUtils.isUnset(rate) && !trn.tradeCurrency.equals(currency.code, ignoreCase = true)) {
            return IsoCurrencyPair(from = from.code, to = currency.code)
        }
        return null
    }

    private fun getFxRequest(fxRequests: MutableMap<String?, FxRequest>, tradeDate: String): FxRequest {
        var fxRequest = fxRequests[tradeDate]
        if (fxRequest == null) {
            fxRequest = FxRequest(tradeDate, ArrayList())
            fxRequests[tradeDate] = fxRequest
        }
        return fxRequest
    }

    fun setTrnRates(portfolio: Portfolio, trnInput: TrnInput) {
        val fxRequest = getFxRequest(portfolio, trnInput)
        val (data) = fxClientService.getRates(fxRequest)
        setRates(data, fxRequest, trnInput)
    }

    fun needsRates(trnInput: TrnInput): Boolean {
        return (
            numberUtils.isUnset(trnInput.tradePortfolioRate) ||
                numberUtils.isUnset(trnInput.tradeBaseRate) ||
                numberUtils.isUnset(trnInput.tradeCashRate)
            ) && trnInput.trnType != TrnType.SPLIT
    }
}
