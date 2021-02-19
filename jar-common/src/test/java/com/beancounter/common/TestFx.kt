package com.beancounter.common

import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.RateCalculator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*

internal class TestFx {

    @Test
    fun is_DefaultPropertiesSet() {
        assertThat(FxRequest("2020-10-01").pairs).isNotNull
    }

    @Test
    @Throws(Exception::class)
    fun is_RateRequestSerializing() {
        val pair = IsoCurrencyPair("THIS", "THAT")
        val fxRequest = FxRequest("")
        fxRequest.addTradeBase(pair)
        fxRequest.addTradePf(pair)
        assertThat(fxRequest.pairs).size().isEqualTo(1)
        val json = BcJson().objectMapper.writeValueAsString(fxRequest)
        val fromJson = BcJson().objectMapper.readValue(json, FxRequest::class.java)
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
        val nzdUsd = IsoCurrencyPair("NZD", "USD")
        val usdUsd = IsoCurrencyPair("USD", "USD")
        val pairs: MutableCollection<IsoCurrencyPair> = ArrayList()
        pairs.add(nzdUsd)
        pairs.add(usdUsd)
        val rateMap: MutableMap<String, FxRate> = HashMap()
        val nzd = Currency("NZD")
        val usd = Currency("USD")
        val nzdRate = FxRate(usd, nzd, BigDecimal.TEN, null)
        rateMap["NZD"] = nzdRate
        val usdRate = FxRate(usd, usd, BigDecimal.ONE, null)

        rateMap["USD"] = usdRate
        val fxPairResults = RateCalculator.compute("2019/08/27", pairs, rateMap)

        val fxResponse = FxResponse(fxPairResults)
        val json = BcJson().objectMapper.writeValueAsString(fxResponse)
        val fromJson = BcJson().objectMapper.readValue(json, FxResponse::class.java)

        assertThat(fromJson.data).isNotNull
        assertThat(fromJson.data.rates).hasSize(2).containsKeys(nzdUsd, usdUsd)
    }

}