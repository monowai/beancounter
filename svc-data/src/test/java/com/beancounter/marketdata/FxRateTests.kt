package com.beancounter.marketdata

import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.utils.BcJson.objectMapper
import com.beancounter.common.utils.RateCalculator
import com.beancounter.marketdata.providers.fxrates.EcbRates
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

internal class FxRateTests {
    @Test
    @Throws(Exception::class)
    fun is_FxRateResponseSerializing() {
        val jsonFile = ClassPathResource("contracts/ecb/ecbEarly.json").file
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
            rates["NZD"] = getRate("NZD", "1.5536294691")
            rates["AUD"] = getRate("AUD", "1.48261")
            rates["USD"] = getRate("USD", "1")
            return rates
        }

    private fun getRate(to: String, rate: String): FxRate {
        return FxRate(Currency("USD"), Currency(to), BigDecimal(rate), null)
    }

    companion object {
        private val USD_USD = IsoCurrencyPair("USD", "USD")
        private val AUD_NZD = IsoCurrencyPair("AUD", "NZD")
        private val NZD_AUD = IsoCurrencyPair("NZD", "AUD")
        private val AUD_USD = IsoCurrencyPair("AUD", "USD")
        private val USD_AUD = IsoCurrencyPair("USD", "AUD")
    }
}