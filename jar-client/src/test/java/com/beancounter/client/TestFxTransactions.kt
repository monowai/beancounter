package com.beancounter.client

import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.utils.CurrencyUtils
import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.math.BigDecimal
import java.util.HashMap

class TestFxTransactions {
    @Test
    fun is_TrnDefaultsSetting() {
        val tradeBase = IsoCurrencyPair("USD", "NZD")
        val tradePf = IsoCurrencyPair("USD", "NZD")
        val tradeCash = IsoCurrencyPair("USD", "NZD")
        val mapRates: MutableMap<IsoCurrencyPair, FxRate> = HashMap()
        val temp = CurrencyUtils().getCurrency("TEMP")
        val one = FxRate(temp, temp, BigDecimal.ONE, null)
        mapRates[tradeBase] = one
        mapRates[tradePf] = one
        mapRates[tradeCash] = one
        val pairResults = FxPairResults()
        pairResults.rates = mapRates
        val fxRequest = FxRequest()
        fxRequest.addTradeBase(tradeBase)
        fxRequest.addTradePf(tradePf)
        fxRequest.addTradeCash(tradeCash)
        val trnInput = TrnInput(CallerRef(), "ABC", price = BigDecimal.TEN)
        val fxTransactions = FxTransactions(
            Mockito.mock(FxService::class.java),
            DateUtils()
        )
        fxTransactions.setRates(pairResults, fxRequest, trnInput)
        Assertions.assertThat(trnInput)
            .hasFieldOrPropertyWithValue("tradeCashRate", BigDecimal.ONE)
            .hasFieldOrPropertyWithValue("tradeBaseRate", BigDecimal.ONE)
            .hasFieldOrPropertyWithValue("tradePortfolioRate", BigDecimal.ONE)
    }
}
