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
import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.math.BigDecimal

/**
 * Client side FX tests.
 */

class TestFxTransactions {
    @Test
    fun `should set transaction defaults`() {
        val tradeBase =
            IsoCurrencyPair(
                USD.code,
                NZD.code
            )
        val tradePf =
            IsoCurrencyPair(
                USD.code,
                NZD.code
            )
        val tradeCash =
            IsoCurrencyPair(
                USD.code,
                NZD.code
            )
        val mapRates: MutableMap<IsoCurrencyPair, FxRate> = HashMap()
        val temp = Currency("TEMP")
        val one =
            FxRate(
                from = temp,
                to = temp,
                rate = BigDecimal.ONE
            )
        mapRates[tradeBase] = one
        mapRates[tradePf] = one
        mapRates[tradeCash] = one
        val pairResults = FxPairResults()
        pairResults.rates = mapRates
        val fxRequest = FxRequest()
        fxRequest.addTradeBase(tradeBase)
        fxRequest.addTradePf(tradePf)
        fxRequest.addTradeCash(tradeCash)
        val trnInput =
            TrnInput(
                CallerRef(),
                "ABC",
                price = BigDecimal.TEN
            )
        val fxTransactions =
            FxTransactions(
                Mockito.mock(FxService::class.java)
            )
        fxTransactions.setRates(
            pairResults,
            fxRequest,
            trnInput
        )
        assertThat(trnInput)
            .hasFieldOrPropertyWithValue(
                "tradeCashRate",
                BigDecimal.ONE
            ).hasFieldOrPropertyWithValue(
                "tradeBaseRate",
                BigDecimal.ONE
            ).hasFieldOrPropertyWithValue(
                "tradePortfolioRate",
                BigDecimal.ONE
            )
    }

    @Test
    fun `needsRates is false when tradeDate is in the future`() {
        // Corporate-event TRNs (DIVI) are created with tradeDate = payDate, which can be
        // up to ~18 days in the future. FX providers (Frankfurter, ECB) reject forward
        // dates — calling them logs "Both FX providers failed". Defer FX resolution to
        // auto-settle when the date actually arrives. (Sentry DATA-51)
        val fxTransactions =
            FxTransactions(
                Mockito.mock(FxService::class.java)
            )
        val tomorrow = DateUtils().date.plusDays(1)
        val trnInput =
            TrnInput(
                CallerRef(),
                "ABC",
                tradeCurrency = "NZD",
                tradeDate = tomorrow
            )
        assertThat(fxTransactions.needsRates(trnInput)).isFalse()
    }

    @Test
    fun `needsRates is true when tradeDate is today and rates are unset`() {
        val fxTransactions =
            FxTransactions(
                Mockito.mock(FxService::class.java)
            )
        val today = DateUtils().date
        val trnInput =
            TrnInput(
                CallerRef(),
                "ABC",
                tradeCurrency = "NZD",
                tradeDate = today
            )
        assertThat(fxTransactions.needsRates(trnInput)).isTrue()
    }

    @Test
    fun `should work with FX pairs`() {
        val fxTransactions =
            FxTransactions(
                Mockito.mock(FxService::class.java)
            )

        assertThat(
            fxTransactions.pair(
                Currency("NZD"),
                Currency("USD"),
                BigDecimal.TEN
            )
        ).isNull() // We have a rate, so don't request one
        assertThat(
            fxTransactions.pair(
                Currency("USD"),
                Currency("USD"),
                null
            )
        ).isNull() // Same currencies, so no pairing
        assertThat(
            fxTransactions.pair(
                Currency("NZD"),
                Currency("USD"),
                // No rate, so service should obtain one.
                null
            )
        ).isNotNull
            .hasFieldOrPropertyWithValue(
                "from",
                "NZD"
            ).hasFieldOrPropertyWithValue(
                "to",
                "USD"
            )
    }
}