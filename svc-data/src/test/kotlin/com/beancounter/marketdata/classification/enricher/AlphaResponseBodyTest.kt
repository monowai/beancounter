package com.beancounter.marketdata.classification.enricher

import com.beancounter.marketdata.providers.alpha.AlphaConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [AlphaResponseBody] decides whether an AlphaVantage body is a quota signal, an error, or a
 * payload worth parsing - before any parse, because a quota body parses cleanly into nothing.
 */
class AlphaResponseBodyTest {
    private val objectMapper = AlphaConfig().getObjectMapper()

    @Test
    fun `a top-level Information key is a quota signal`() {
        assertThat(AlphaResponseBody.of("""{"Information": "call frequency exceeded"}""", objectMapper))
            .isEqualTo(AlphaResponseBody.RATE_LIMITED)
    }

    @Test
    fun `a top-level Note key is a quota signal even when it mentions an Error`() {
        assertThat(AlphaResponseBody.of("""{"Note": "Error: 5 calls per minute"}""", objectMapper))
            .isEqualTo(AlphaResponseBody.RATE_LIMITED)
    }

    @Test
    fun `an Error Message body is an error`() {
        assertThat(AlphaResponseBody.of("""{"Error Message": "Invalid API call"}""", objectMapper))
            .isEqualTo(AlphaResponseBody.ERROR)
    }

    @Test
    fun `a blank body is an error`() {
        assertThat(AlphaResponseBody.of("  ", objectMapper)).isEqualTo(AlphaResponseBody.ERROR)
    }

    @Test
    fun `a body that is not JSON and names no error is a payload`() {
        assertThat(AlphaResponseBody.of("not json", objectMapper)).isEqualTo(AlphaResponseBody.PAYLOAD)
    }

    @Test
    fun `a well-formed overview is a payload`() {
        assertThat(AlphaResponseBody.of("""{"Symbol": "XYZ", "Sector": "Technology"}""", objectMapper))
            .isEqualTo(AlphaResponseBody.PAYLOAD)
    }
}