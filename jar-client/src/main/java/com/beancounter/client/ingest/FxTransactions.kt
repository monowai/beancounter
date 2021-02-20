package com.beancounter.client.ingest

import com.beancounter.client.FxService
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Currency
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.CurrencyUtils
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.NumberUtils
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class FxTransactions(private val fxService: FxService, private val dateUtils: DateUtils) {
    private val currencyUtils = CurrencyUtils()
    private val numberUtils = NumberUtils()

    fun buildRequest(portfolio: Portfolio, trn: TrnInput): FxRequest {
        val fxRequestMap: MutableMap<String?, FxRequest> = HashMap()
        val tradeDate = dateUtils.getDateString(trn.tradeDate)
        val fxRequest = getFxRequest(fxRequestMap, tradeDate)
        fxRequest.addTradePf(
            pair(portfolio.currency, trn, trn.tradePortfolioRate)
        )
        fxRequest.addTradeBase(
            pair(portfolio.base, trn, trn.tradeBaseRate)
        )
        if (trn.cashCurrency != null) {
            fxRequest.addTradeCash(
                pair(currencyUtils.getCurrency(trn.cashCurrency!!), trn, trn.tradeCashRate)
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

    private fun pair(currency: Currency, trn: TrnInput, rate: BigDecimal?): IsoCurrencyPair? {
        return currencyUtils.getCurrencyPair(
            rate,
            currencyUtils.getCurrency(trn.tradeCurrency!!),
            currency
        )
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
        val (data) = fxService.getRates(fxRequest)
        setRates(data, fxRequest, trnInput)
    }
}
