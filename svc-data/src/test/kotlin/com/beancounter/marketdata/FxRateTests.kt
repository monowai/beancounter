package com.beancounter.marketdata

import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.RateCalculator
import com.beancounter.marketdata.Constants.Companion.AUD
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.integ.FxMvcTests.Companion.fxMock
import com.beancounter.marketdata.providers.fxrates.EcbRates
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Unit test FX assumptions.
 */
internal class FxRateTests {
    private val objectMapper: ObjectMapper = BcJson().objectMapper
    @Test
    @Throws(Exception::class)
    fun is_FxRateResponseSerializing() {
        val jsonFile = ClassPathResource("$fxMock/ecbEarly.json").file
        val ecbRates = objectMapper.readValue(jsonFile, EcbRates::class.java)
        Assertions.assertThat(ecbRates)
            .isNotNull
            .hasNoNullFieldsOrProperties()
        Assertions.assertThat(ecbRates.rates).hasSize(6)
    }

    @Test
    fun is_RateCalculatorComputing() {
        val pairs = getCurrencyPairs(USD_USD, AUD_NZD, NZD_AUD, AUD_USD, USD_AUD)
        val rates = rateTable
        val asAt = "2019-11-21"
        val (rates1) = RateCalculator.compute(asAt, pairs, rates)
        val audUsd = rates1[AUD_USD]
        val usdAud = rates1[USD_AUD]
        // Verify that the inverse rate is equal
        val calc = BigDecimal.ONE.divide(audUsd!!.rate, 8, RoundingMode.HALF_UP)
        Assertions.assertThat(usdAud!!.rate).isEqualTo(calc)
    }

    private fun getCurrencyPairs(vararg pairs: IsoCurrencyPair): Collection<IsoCurrencyPair> {
        val results = ArrayList<IsoCurrencyPair>()
        for (pair in pairs) {
            results.add(pair)
        }
        return results
    }

    private val rateTable: Map<String, FxRate>
        get() {
            val rates: MutableMap<String, FxRate> = HashMap()
            rates[NZD.code] = getRate(NZD.code, "1.5536294691")
            rates[AUD.code] = getRate(AUD.code, "1.48261")
            rates[USD.code] = getRate(USD.code, "1")
            return rates
        }

    private fun getRate(to: String, rate: String): FxRate {
        return FxRate(USD, Currency(to), BigDecimal(rate), null)
    }

    companion object {
        private val USD_USD = IsoCurrencyPair(USD.code, USD.code)
        private val AUD_NZD = IsoCurrencyPair(AUD.code, NZD.code)
        private val NZD_AUD = IsoCurrencyPair(NZD.code, AUD.code)
        private val AUD_USD = IsoCurrencyPair(AUD.code, USD.code)
        private val USD_AUD = IsoCurrencyPair(USD.code, AUD.code)
    }
}
