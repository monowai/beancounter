package com.beancounter.marketdata.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.Security

/**
 * Guards the DNS-resilience side effects of the external API client config.
 */
class ExternalApiRestClientConfigTest {
    @Test
    fun `disables JVM negative-DNS caching so one miss does not poison the batch`() {
        // Constructing the config applies the side effect.
        ExternalApiRestClientConfig()

        assertThat(Security.getProperty("networkaddress.cache.negative.ttl"))
            .describedAs("negative-DNS cache must be off so a transient miss isn't cached")
            .isEqualTo("0")
    }

    @Test
    fun `builds pooled RestClients for external providers`() {
        val config = ExternalApiRestClientConfig()

        // A pooled HttpComponents-backed client builds without error for each provider URL.
        assertThat(config.eodhdRestClient("http://localhost:0")).isNotNull
        assertThat(config.alphaVantageRestClient("http://localhost:0")).isNotNull
    }
}