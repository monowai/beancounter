package com.beancounter.client.ingest

import com.beancounter.client.FxService
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Currency
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.NumberUtils
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
/**
 * Client side service to obtain FX rates in response to Transaction requests.
 */
class FxTransactions(
    private val fxClientService: FxService
) {
    private val numberUtils = NumberUtils()

    fun buildRequest(portfolio: Portfolio, trn: TrnInput): FxRequest {
        val fxRequestMap: MutableMap<String?, FxRequest> = HashMap()
        val tradeDate = trn.tradeDate.toString()
        val fxRequest = getFxRequest(fxRequestMap, tradeDate)
        fxRequest.addTradePf(
            pair(portfolio.currency, trn, trn.tradePortfolioRate)
        )
        fxRequest.addTradeBase(
            pair(portfolio.base, trn, trn.tradeBaseRate)
        )
        if (trn.cashCurrency != null) {
            fxRequest.addTradeCash(
                pair(Currency(trn.cashCurrency!!), trn, trn.tradeCashRate)
            )
        }
        return fxRequest
    }

    fun setRates(
        rates: FxPairResults,
        fxRequest: FxRequest,
        trn: TrnInput
    ) {
        if (fxRequest.tradePf != null && numberUtils.isUnset(trn.tradePortfolioRate)) {
            trn.tradePortfolioRate = rates.rates[fxRequest.tradePf!!]!!.rate
        } else {
            trn.tradePortfolioRate = BigDecimal.ONE
        }
        if (fxRequest.tradeBase != null && numberUtils.isUnset(trn.tradeBaseRate)) {
            trn.tradeBaseRate = rates.rates[fxRequest.tradeBase!!]!!.rate
        } else {
            trn.tradeBaseRate = BigDecimal.ONE
        }
        if (fxRequest.tradeCash != null && numberUtils.isUnset(trn.tradeCashRate)) {
            trn.tradeCashRate = rates.rates[fxRequest.tradeCash!!]!!.rate
        } else {
            trn.tradeCashRate = BigDecimal.ONE
        }
    }

    fun pair(currency: Currency, trn: TrnInput, rate: BigDecimal?): IsoCurrencyPair? {
        val from = Currency(trn.tradeCurrency)
        if (rate == null && !trn.tradeCurrency.equals(currency.code, ignoreCase = true)) {
            return IsoCurrencyPair(from = from.code, to = currency.code)
        }
        return null
    }

    private fun getFxRequest(fxRequests: MutableMap<String?, FxRequest>, tradeDate: String?): FxRequest {
        var fxRequest = fxRequests[tradeDate]
        if (fxRequest == null) {
            fxRequest = FxRequest(tradeDate!!, ArrayList())
            fxRequests[tradeDate] = fxRequest
        }
        return fxRequest
    }

    fun setTrnRates(portfolio: Portfolio, trnInput: TrnInput) {
        val fxRequest = buildRequest(portfolio, trnInput)
        val (data) = fxClientService.getRates(fxRequest)
        setRates(data, fxRequest, trnInput)
    }
}
