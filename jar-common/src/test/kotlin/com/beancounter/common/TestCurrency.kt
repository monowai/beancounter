package com.beancounter.common

import com.beancounter.common.contracts.CurrencyResponse
import com.beancounter.common.model.Currency
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.IsoCurrencyPair.Companion.toPair
import com.beancounter.common.utils.BcJson
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Simple currency behaviour assertions.
 */
internal class TestCurrency {
    private val bcJson = BcJson()
    private val objectMapper = bcJson.objectMapper

    @Test
    @Throws(Exception::class)
    fun is_CurrencySerializing() {
        val currency = Currency("SomeCode", "Some Name", "$")
        assertThat(currency).isNotNull
        val json = objectMapper.writeValueAsString(currency)
        val fromJson = objectMapper.readValue(json, Currency::class.java)
        assertThat(fromJson).isEqualTo(currency)
    }

    @Test
    @Throws(Exception::class)
    fun is_CurrencyResponseSerializing() {
        val currencies: MutableCollection<Currency> = ArrayList()
        val currency = Currency("SomeId", "Some Name", "$")
        currencies.add(currency)
        val currencyResponse = CurrencyResponse(currencies)
        val json = objectMapper.writeValueAsString(currencyResponse)
        val fromJson = objectMapper.readValue(json, CurrencyResponse::class.java)
        assertThat(fromJson).isEqualTo(currencyResponse)
    }

    @Test
    fun is_GetCurrencyWorking() {
        val currency = Currency("NZD")
        assertThat(currency).hasFieldOrPropertyWithValue("code", "NZD")
    }

    @Test
    fun is_CurrencyPairConsistent() {
        val trade = "NZD"
        val report = "USD"
        val byCode = IsoCurrencyPair(report, trade)
        val byCurrency = toPair(Currency(report), Currency(trade))
        assertThat(byCode).usingRecursiveComparison().isEqualTo(byCurrency)
        assertThat(
            toPair(
                Currency(report),
                Currency(report),
            ),
        )
            .isNull()
    }

    @Test
    fun is_DefaultsCorrect() {
        val nzd = Currency("NZD")
        assertThat(nzd).hasNoNullFieldsOrProperties()

        // Mutable
        nzd.name = "Aotearoa"
        nzd.symbol = "%"

        assertThat(nzd)
            .hasFieldOrPropertyWithValue("name", "Aotearoa")
            .hasFieldOrPropertyWithValue("symbol", "%")
    }

    @Test
    fun is_CurrencyContractHonoured() {
        val nzd = Currency("NZD")
        val sgd = Currency("SGD")
        assertThat(nzd).isNotEqualTo(sgd)
        val currencies = HashMap<Currency, Currency>()
        currencies[nzd] = nzd
        currencies[sgd] = sgd
        assertThat(currencies)
            .hasSize(2)
            .containsKeys(nzd, sgd)
    }

    @Test
    fun is_IsoPairContractHonoured() {
        val pairA = IsoCurrencyPair("USD", "NZD")
        val pairB = IsoCurrencyPair("USD", "NZD")
        val pairs = HashMap<IsoCurrencyPair, IsoCurrencyPair>()
        pairs[pairA] = pairA
        pairs[pairB] = pairB
        assertThat(pairs).hasSize(1).containsKeys(pairA, pairB)
    }
}
