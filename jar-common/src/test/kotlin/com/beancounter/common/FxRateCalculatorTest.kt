package com.beancounter.common

import com.beancounter.common.TestMarkets.Companion.NZD
import com.beancounter.common.TestMarkets.Companion.USD
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.FxRateCalculator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

private const val pFromCode = "from.code"

private const val pToCode = "to.code"

private const val pRate = "rate"

/**
 * FX Request response tests.
 */
internal class FxRateCalculatorTest {
    private val objectMapper = BcJson().objectMapper

    @Test
    fun is_DefaultPropertiesSet() {
        assertThat(FxRequest("2020-10-01").pairs).isNotNull
    }

    @Test
    fun is_FxRequestSerializationAssumptions() {
        val fxRequest = FxRequest()
        fxRequest.addTradeBase(IsoCurrencyPair(USD.code, NZD.code))
        fxRequest.addTradePf(IsoCurrencyPair(USD.code, NZD.code))
        fxRequest.addTradeCash(IsoCurrencyPair(USD.code, NZD.code))
        val json = objectMapper.writeValueAsString(fxRequest)
        val fromJson = objectMapper.readValue(json, FxRequest::class.java)
        assertThat(fromJson)
            .hasAllNullFieldsOrPropertiesExcept("rateDate", "pairs")
            .isEqualTo(fxRequest)
        assertThat(fromJson.pairs).hasSize(1)
    }

    @Test
    @Throws(Exception::class)
    fun is_RateRequestSerializing() {
        val pair = IsoCurrencyPair("THIS", "THAT")
        val fxRequest = FxRequest("")
        fxRequest.addTradeBase(pair)
        fxRequest.addTradePf(pair)
        assertThat(fxRequest.pairs).size().isEqualTo(1)
        val json = objectMapper.writeValueAsString(fxRequest)
        val fromJson = objectMapper.readValue(json, FxRequest::class.java)
        assertThat(fromJson.pairs).hasSize(1)
        assertThat(fromJson.tradeBase).isNull()
        assertThat(fromJson.tradePf).isNull()
        assertThat(fromJson.tradeCash).isNull()
    }

    @Test
    fun is_FxRequestPairsIgnoringDuplicates() {
        val pair = IsoCurrencyPair("THIS", "THAT")
        val fxRequest = FxRequest("")
        fxRequest.add(pair)
        fxRequest.add(pair)
        assertThat(fxRequest.pairs).hasSize(1)
    }

    @Test
    @Throws(Exception::class)
    fun is_FxResultSerializing() {
        val nzdUsd = IsoCurrencyPair(NZD.code, USD.code)
        val usdNzd = IsoCurrencyPair(USD.code, NZD.code)
        val usdUsd = IsoCurrencyPair(USD.code, USD.code)
        val pairs = arrayListOf(nzdUsd, usdNzd, usdUsd)

        val rawRate = "1.41030000"
        val crossRate = ".70906899"
        val fxPairResults = FxRateCalculator.compute(
            currencyPairs = pairs,
            rateMap = mapOf(
                Pair(NZD.code, FxRate(USD, NZD, BigDecimal(rawRate))),
                Pair(USD.code, FxRate(USD, USD, BigDecimal.ONE)),

            ),
        )

        val fromJson =
            objectMapper.readValue(
                objectMapper.writeValueAsString(FxResponse(fxPairResults)),
                FxResponse::class.java,
            )

        assertThat(fromJson.data).isNotNull
        assertThat(fromJson.data.rates).hasSize(3).containsKeys(nzdUsd, usdUsd, usdNzd)
        assertThat(fromJson.data.rates[usdUsd])
            .hasFieldOrPropertyWithValue(pFromCode, USD.code)
            .hasFieldOrPropertyWithValue(pToCode, USD.code)
            .hasFieldOrPropertyWithValue(pRate, BigDecimal.ONE)
        assertThat(fromJson.data.rates[nzdUsd])
            .hasFieldOrPropertyWithValue(pFromCode, NZD.code)
            .hasFieldOrPropertyWithValue(pToCode, USD.code)
            .hasFieldOrPropertyWithValue(pRate, BigDecimal(crossRate)) // Inverts the rate as USD is involved
        assertThat(fromJson.data.rates[usdNzd])
            .hasFieldOrPropertyWithValue(pFromCode, USD.code)
            .hasFieldOrPropertyWithValue(pToCode, NZD.code)
            .hasFieldOrPropertyWithValue(pRate, BigDecimal(rawRate)) // Raw rate
    }
}