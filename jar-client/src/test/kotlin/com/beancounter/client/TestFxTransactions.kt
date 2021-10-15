package com.beancounter.client

import com.beancounter.client.Constants.Companion.NZD
import com.beancounter.client.Constants.Companion.USD
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.math.BigDecimal

/**
 * Client side FX tests.
 */

class TestFxTransactions {
    @Test
    fun is_TrnDefaultsSetting() {
        val tradeBase = IsoCurrencyPair(USD.code, NZD.code)
        val tradePf = IsoCurrencyPair(USD.code, NZD.code)
        val tradeCash = IsoCurrencyPair(USD.code, NZD.code)
        val mapRates: MutableMap<IsoCurrencyPair, FxRate> = HashMap()
        val temp = Currency("TEMP")
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
            Mockito.mock(FxService::class.java)
        )
        fxTransactions.setRates(pairResults, fxRequest, trnInput)
        assertThat(trnInput)
            .hasFieldOrPropertyWithValue("tradeCashRate", BigDecimal.ONE)
            .hasFieldOrPropertyWithValue("tradeBaseRate", BigDecimal.ONE)
            .hasFieldOrPropertyWithValue("tradePortfolioRate", BigDecimal.ONE)
    }

    @Test
    fun is_FxPairsWorking() {
        val fxTransactions = FxTransactions(Mockito.mock(FxService::class.java))

        assertThat(
            fxTransactions.pair(
                Currency("NZD"),
                TrnInput(assetId = "ABC", tradeCurrency = "USD", price = BigDecimal.TEN),
                BigDecimal.TEN,
            )
        )
            .isNull() // We have a rate, so don't request one
        assertThat(
            fxTransactions.pair(
                Currency("USD"),
                TrnInput(assetId = "ABC", tradeCurrency = "USD", price = BigDecimal.TEN),
                null
            )
        )
            .isNull()
        assertThat(
            fxTransactions.pair(
                Currency("USD"),
                TrnInput(assetId = "ABC", tradeCurrency = "NZD", price = BigDecimal.TEN),
                null
            )
        )
            .isNotNull
            .hasFieldOrPropertyWithValue("from", "NZD")
            .hasFieldOrPropertyWithValue("to", "USD")
    }
}
