package com.beancounter.common

import com.beancounter.common.TestMarkets.Companion.NZD
import com.beancounter.common.TestMarkets.Companion.USD
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.FxRateCalculator
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

private const val FROM_CODE = "from.code"

private const val TO_CODE = "to.code"

private const val RATE = "rate"

/**
 * FX Request response tests.
 */
internal class FxRateCalculatorTest {
    @Test
    fun is_DefaultPropertiesSet() {
        assertThat(FxRequest("2020-10-01").pairs).isNotNull
    }

    @Test
    fun is_FxRequestSerializationAssumptions() {
        val fxRequest = FxRequest()
        fxRequest.addTradeBase(
            IsoCurrencyPair(
                USD.code,
                NZD.code
            )
        )
        fxRequest.addTradePf(
            IsoCurrencyPair(
                USD.code,
                NZD.code
            )
        )
        fxRequest.addTradeCash(
            IsoCurrencyPair(
                USD.code,
                NZD.code
            )
        )
        val json = objectMapper.writeValueAsString(fxRequest)
        val fromJson = objectMapper.readValue<FxRequest>(json)
        assertThat(fromJson)
            .hasAllNullFieldsOrPropertiesExcept(
                "rateDate",
                "pairs"
            ).isEqualTo(fxRequest)
        assertThat(fromJson.pairs).hasSize(1)
    }

    @Test
    @Throws(Exception::class)
    fun is_RateRequestSerializing() {
        val pair =
            IsoCurrencyPair(
                "THIS",
                "THAT"
            )
        val fxRequest = FxRequest("")
        fxRequest.addTradeBase(pair)
        fxRequest.addTradePf(pair)
        assertThat(fxRequest.pairs).size().isEqualTo(1)
        val json = objectMapper.writeValueAsString(fxRequest)
        val fromJson = objectMapper.readValue<FxRequest>(json)
        assertThat(fromJson.pairs).hasSize(1)
        assertThat(fromJson.tradeBase).isNull()
        assertThat(fromJson.tradePf).isNull()
        assertThat(fromJson.tradeCash).isNull()
    }

    @Test
    fun is_FxRequestPairsIgnoringDuplicates() {
        val pair =
            IsoCurrencyPair(
                "THIS",
                "THAT"
            )
        val fxRequest =
            FxRequest(
                "",
                mutableSetOf(
                    pair,
                    pair
                )
            )
        fxRequest.add(pair)
        fxRequest.add(pair)
        assertThat(fxRequest.pairs).hasSize(1)
    }

    @Test
    fun `Verify FX Response Serialization and Data Integrity`() {
        val nzdUsd =
            IsoCurrencyPair(
                NZD.code,
                USD.code
            )
        val usdNzd =
            IsoCurrencyPair(
                USD.code,
                NZD.code
            )
        val usdUsd =
            IsoCurrencyPair(
                USD.code,
                USD.code
            )
        val pairs =
            arrayListOf(
                nzdUsd,
                usdNzd,
                usdUsd
            )

        val rawRate = "1.41030000"
        val crossRate = ".70906899"
        val fxPairResults =
            FxRateCalculator.compute(
                currencyPairs = pairs,
                rateMap =
                    mapOf(
                        Pair(
                            NZD.code,
                            FxRate(
                                from = USD,
                                to = NZD,
                                rate = BigDecimal(rawRate)
                            )
                        ),
                        Pair(
                            USD.code,
                            FxRate(
                                from = USD,
                                to = USD,
                                rate = BigDecimal.ONE
                            )
                        )
                    )
            )

        // Verify serialization works without error.
        val fxResponseJson = objectMapper.writeValueAsString(FxResponse(fxPairResults))
        val fromJson = objectMapper.readValue<FxResponse>(fxResponseJson)

        with(fromJson) {
            assertThat(data).isNotNull
            assertThat(data.rates)
                .hasSize(3)
                .containsKeys(
                    nzdUsd,
                    usdUsd,
                    usdNzd
                )
            assertThat(data.rates[usdUsd])
                .hasFieldOrPropertyWithValue(
                    FROM_CODE,
                    USD.code
                ).hasFieldOrPropertyWithValue(
                    TO_CODE,
                    USD.code
                ).hasFieldOrPropertyWithValue(
                    RATE,
                    BigDecimal.ONE
                )
            assertThat(data.rates[nzdUsd])
                .hasFieldOrPropertyWithValue(
                    FROM_CODE,
                    NZD.code
                ).hasFieldOrPropertyWithValue(
                    TO_CODE,
                    USD.code
                ).hasFieldOrPropertyWithValue(
                    RATE,
                    BigDecimal(crossRate)
                ) // Inverts the rate as USD is involved
            assertThat(data.rates[usdNzd])
                .hasFieldOrPropertyWithValue(
                    FROM_CODE,
                    USD.code
                ).hasFieldOrPropertyWithValue(
                    TO_CODE,
                    NZD.code
                ).hasFieldOrPropertyWithValue(
                    RATE,
                    BigDecimal(rawRate)
                ) // Raw rate
        }
    }
}