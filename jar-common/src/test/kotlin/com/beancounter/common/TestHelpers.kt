package com.beancounter.common

import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.model.Status
import com.beancounter.common.utils.BcJson
import com.fasterxml.jackson.core.type.TypeReference
import org.assertj.core.api.Assertions.assertThat

/**
 * Shared test utilities to reduce code duplication and simplify common test patterns.
 *
 * This object provides:
 * - Factory methods for creating test objects (assets, currencies, markets)
 * - Generic serialization testing utilities
 * - Exception testing utilities
 * - Common assertion patterns
 *
 * Usage:
 * ```
 * val asset = TestHelpers.createTestAsset("AAPL", "NASDAQ")
 * TestHelpers.assertSerializationRoundTrip(asset)
 * ```
 */
object TestHelpers {
    /**
     * Creates a test asset with default values.
     */
    fun createTestAsset(
        code: String = "TEST",
        market: String = "TEST",
        name: String = "Test Asset",
        category: String = "STOCK"
    ): Asset =
        Asset(
            code = code,
            id = code,
            name = name,
            market = Market(market),
            status = Status.Active,
            assetCategory = AssetCategory(category, category)
        )

    /**
     * Creates a test currency with default values.
     */
    fun createTestCurrency(code: String = "USD"): Currency = Currency(code)

    /**
     * Generic serialization test helper that serializes and deserializes an object.
     */
    inline fun <reified T> assertSerializationRoundTrip(obj: T): T {
        val json = BcJson.objectMapper.writeValueAsString(obj)
        val deserialized = BcJson.objectMapper.readValue(json, T::class.java)
        assertThat(deserialized).isEqualTo(obj)
        return deserialized
    }

    /**
     * Generic serialization test helper for collections.
     */
    inline fun <reified T> assertCollectionSerializationRoundTrip(
        collection: Collection<T>,
        typeReference: TypeReference<Collection<T>>
    ): Collection<T> {
        val json = BcJson.objectMapper.writeValueAsString(collection)
        val deserialized = BcJson.objectMapper.readValue(json, typeReference)
        assertThat(deserialized).isEqualTo(collection)
        return deserialized
    }

    /**
     * Asserts that an exception is thrown with the expected message.
     */
    inline fun <reified T : Exception> assertThrowsWithMessage(
        message: String,
        noinline block: () -> Unit
    ): T {
        val exception =
            org.junit.jupiter.api.Assertions
                .assertThrows(T::class.java, block)
        assertThat(exception.message).isEqualTo(message)
        return exception
    }
}