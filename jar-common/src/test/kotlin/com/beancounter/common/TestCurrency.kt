package com.beancounter.common

import com.beancounter.common.contracts.CurrencyResponse
import com.beancounter.common.model.Currency
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.IsoCurrencyPair.Companion.toPair
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Test suite for Currency model to ensure proper serialization, deserialization, and behavior.
 *
 * This class tests:
 * - Currency object serialization and deserialization
 * - Currency response handling
 * - Currency pair consistency
 * - Default value handling
 * - Currency contract compliance
 */
internal class TestCurrency {
    @Test
    fun `should serialize and deserialize currency correctly`() {
        val currency = TestHelpers.createTestCurrency("SomeCode")
        TestHelpers.assertSerializationRoundTrip(currency)
    }

    @Test
    fun `should serialize and deserialize currency response correctly`() {
        val currencies: MutableCollection<Currency> = ArrayList()
        val currency = TestHelpers.createTestCurrency("SomeId")
        currencies.add(currency)
        val currencyResponse = CurrencyResponse(currencies)
        TestHelpers.assertSerializationRoundTrip(currencyResponse)
    }

    @Test
    fun `should serialize and deserialize currency collection correctly`() {
        val currencies =
            listOf(
                TestHelpers.createTestCurrency("USD"),
                TestHelpers.createTestCurrency("EUR"),
                TestHelpers.createTestCurrency("GBP")
            )
        TestHelpers.assertCollectionSerializationRoundTrip(
            collection = currencies,
            typeReference = object : com.fasterxml.jackson.core.type.TypeReference<Collection<Currency>>() {}
        )
    }

    @Test
    fun `should create currency with correct code`() {
        val currency = TestHelpers.createTestCurrency("NZD")
        assertThat(currency).hasFieldOrPropertyWithValue(
            "code",
            "NZD"
        )
    }

    @Test
    fun `should maintain currency pair consistency`() {
        val trade = "NZD"
        val report = "USD"
        val byCode =
            IsoCurrencyPair(
                report,
                trade
            )
        val byCurrency =
            toPair(
                TestHelpers.createTestCurrency(report),
                TestHelpers.createTestCurrency(trade)
            )
        assertThat(byCode).usingRecursiveComparison().isEqualTo(byCurrency)
        assertThat(
            toPair(
                TestHelpers.createTestCurrency(report),
                TestHelpers.createTestCurrency(report)
            )
        ).isNull()
    }

    @Test
    fun `should handle default values correctly`() {
        val nzd = TestHelpers.createTestCurrency("NZD")
        assertThat(nzd).hasNoNullFieldsOrProperties()

        // Mutable
        nzd.name = "Aotearoa"
        nzd.symbol = "%"

        assertThat(nzd)
            .hasFieldOrPropertyWithValue(
                "name",
                "Aotearoa"
            ).hasFieldOrPropertyWithValue(
                "symbol",
                "%"
            )
    }

    @Test
    fun `should honor currency contract`() {
        val nzd = Currency("NZD")
        val sgd = Currency("SGD")
        assertThat(nzd).isNotEqualTo(sgd)
        val currencies = HashMap<Currency, Currency>()
        currencies[nzd] = nzd
        currencies[sgd] = sgd
        assertThat(currencies)
            .hasSize(2)
            .containsKeys(
                nzd,
                sgd
            )
    }

    @Test
    fun `should honor ISO pair contract`() {
        val pairA =
            IsoCurrencyPair(
                "USD",
                "NZD"
            )
        val pairB =
            IsoCurrencyPair(
                "USD",
                "NZD"
            )
        val pairs = HashMap<IsoCurrencyPair, IsoCurrencyPair>()
        pairs[pairA] = pairA
        pairs[pairB] = pairB
        assertThat(pairs).hasSize(1).containsKeys(
            pairA,
            pairB
        )
    }
}