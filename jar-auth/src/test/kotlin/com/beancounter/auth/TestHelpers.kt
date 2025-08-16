package com.beancounter.auth

import com.beancounter.common.utils.BcJson
import org.assertj.core.api.Assertions.assertThat

/**
 * Shared test utilities for jar-auth tests to reduce code duplication and improve consistency.
 *
 * This object provides:
 * - Serialization testing utilities
 * - Common assertion patterns for auth tests
 *
 * Usage:
 * ```
 * TestHelpers.assertSerializationRoundTrip(response)
 * ```
 */
object TestHelpers {
    /**
     * Generic serialization test helper that serializes and deserializes an object.
     */
    inline fun <reified T> assertSerializationRoundTrip(obj: T): T {
        val json = BcJson.objectMapper.writeValueAsString(obj)
        val deserialized = BcJson.objectMapper.readValue(json, T::class.java)
        assertThat(deserialized).isEqualTo(obj)
        return deserialized
    }
}