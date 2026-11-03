package com.beancounter.client.ingest

import com.beancounter.client.FxService
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Currency
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.utils.DateUtils
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Client side service to obtain FX rates in response to Transaction requests. TrnInput is
 * rated at ingest; a Trn is rated at settle, once a deferred forward-dated tradeDate arrives.
 */
@Service
class FxTransactions(
    private val fxClientService: FxService,
    private val dateUtils: DateUtils = DateUtils()
) {
    fun getFxRequest(
        portfolio: Portfolio,
        trn: TrnInput
    ): FxRequest = TrnInputRates(trn).fxRequest(portfolio)

    fun setRates(
        rates: FxPairResults,
        fxRequest: FxRequest,
        trnInput: TrnInput
    ) = TrnInputRates(trnInput).apply(rates, fxRequest)

    fun pair(
        from: Currency,
        to: Currency,
        rate: BigDecimal?
    ): IsoCurrencyPair? = currencyPair(from, to, rate)

    fun needsRates(trnInput: TrnInput): Boolean = TrnInputRates(trnInput).needsRates(dateUtils.date)

    fun needsRates(trn: Trn): Boolean = TrnRates(trn).needsRates(dateUtils.date)

    fun setRates(
        portfolio: Portfolio,
        trnInput: TrnInput
    ) = resolve(portfolio, TrnInputRates(trnInput))

    /**
     * Settle-time FX resolution. PROPOSED corporate-event trns (DIVI) are written with
     * forward-dated tradeDate (= payDate) and their FX rates are deferred (see
     * `needsRates(TrnInput)`). When auto-settle (or a manual settle) flips PROPOSED → SETTLED
     * the tradeDate has now arrived, so we resolve and stamp the rates on the persisted Trn.
     */
    fun setRates(
        portfolio: Portfolio,
        trn: Trn
    ) = resolve(portfolio, TrnRates(trn))

    private fun resolve(
        portfolio: Portfolio,
        tradeRates: TradeRates
    ) {
        if (!tradeRates.needsRates(dateUtils.date)) return
        val fxRequest = tradeRates.fxRequest(portfolio)
        val (data) = getRates(fxRequest)
        tradeRates.apply(data, fxRequest)
    }

    /**
     * A request with no pairs has nothing to convert — trade, portfolio, base and cash
     * all share one currency, so every rate is 1. Skip the round trip: the server
     * resolves rates by date, so an empty request still fails when no rate is cached
     * for that date, rejecting a trade that never needed FX.
     */
    private fun getRates(fxRequest: FxRequest): FxResponse =
        if (fxRequest.pairs.isEmpty()) {
            FxResponse(FxPairResults())
        } else {
            fxClientService.getRates(fxRequest)
        }
}