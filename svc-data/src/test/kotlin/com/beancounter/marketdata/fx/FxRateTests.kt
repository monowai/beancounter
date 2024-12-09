package com.beancounter.marketdata.fx

import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.FxRateCalculator
import com.beancounter.marketdata.Constants.Companion.AUD
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.fx.FxFullStackTest.Companion.FX_MOCK
import com.beancounter.marketdata.fx.fxrates.ExRatesResponse
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import java.math.BigDecimal

/**
 * Unit test FX assumptions.
 */
internal class FxRateTests {
    @Test
    @Throws(Exception::class)
    fun is_FxRateResponseSerializing() {
        val jsonFile = ClassPathResource("$FX_MOCK/ecbEarly.json").file
        val ecbRates = objectMapper.readValue<ExRatesResponse>(jsonFile)
        assertThat(ecbRates)
            .isNotNull
            .hasNoNullFieldsOrProperties()
        assertThat(ecbRates.rates).hasSize(6)
    }

    @Test
    fun is_RateCalculatorComputing() {
        val pairs =
            arrayListOf(
                USD_USD,
                AUD_NZD,
                NZD_AUD,
                AUD_USD,
                USD_AUD
            )
        val (rates) =
            FxRateCalculator.compute(
                currencyPairs = pairs,
                rateMap = rateTable
            )
        val audUsd = rates[AUD_USD] // < 1
        val usdAud = rates[USD_AUD] // > 1
        assertThat(audUsd)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                "rate",
                BigDecimal("0.74148222")
            )
        assertThat(usdAud)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                "rate",
                usdAud!!.rate
            )
    }

    private val rateTable: Map<String, FxRate>
        get() {
            val rates: MutableMap<String, FxRate> = HashMap()
            rates[NZD.code] =
                getRate(
                    NZD.code,
                    "1.5536294691"
                )
            rates[AUD.code] =
                getRate(
                    AUD.code,
                    "1.34865"
                )
            rates[USD.code] =
                getRate(
                    USD.code,
                    "1"
                )
            return rates
        }

    private fun getRate(
        to: String,
        rate: String
    ): FxRate =
        FxRate(
            from = USD,
            to = Currency(to),
            rate = BigDecimal(rate)
        )

    companion object {
        private val USD_USD =
            IsoCurrencyPair(
                USD.code,
                USD.code
            )
        private val AUD_NZD =
            IsoCurrencyPair(
                AUD.code,
                NZD.code
            )
        private val NZD_AUD =
            IsoCurrencyPair(
                NZD.code,
                AUD.code
            )
        private val AUD_USD =
            IsoCurrencyPair(
                AUD.code,
                USD.code
            )
        private val USD_AUD =
            IsoCurrencyPair(
                USD.code,
                AUD.code
            )
    }
}