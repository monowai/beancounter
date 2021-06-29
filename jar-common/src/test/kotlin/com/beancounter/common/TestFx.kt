package com.beancounter.common

import com.beancounter.common.TestMarkets.Companion.NZD
import com.beancounter.common.TestMarkets.Companion.USD
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.RateCalculator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * FX Request response tests.
 */
internal class TestFx {
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
        val usdUsd = IsoCurrencyPair(USD.code, USD.code)
        val pairs: MutableCollection<IsoCurrencyPair> = ArrayList()
        pairs.add(nzdUsd)
        pairs.add(usdUsd)
        val rateMap: MutableMap<String, FxRate> = HashMap()
        val nzd = NZD
        val usd = USD
        val nzdRate = FxRate(usd, nzd, BigDecimal.TEN, null)
        rateMap[NZD.code] = nzdRate
        val usdRate = FxRate(usd, usd, BigDecimal.ONE, null)

        rateMap[USD.code] = usdRate
        val fxPairResults = RateCalculator.compute("2019/08/27", pairs, rateMap)

        val fxResponse = FxResponse(fxPairResults)
        val json = objectMapper.writeValueAsString(fxResponse)
        val fromJson = objectMapper.readValue(json, FxResponse::class.java)

        assertThat(fromJson.data).isNotNull
        assertThat(fromJson.data.rates).hasSize(2).containsKeys(nzdUsd, usdUsd)
    }
}
