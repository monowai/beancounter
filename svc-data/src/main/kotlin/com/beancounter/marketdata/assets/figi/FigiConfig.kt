package com.beancounter.marketdata.assets.figi

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * Bloomberg OpenFigi configuration properties
 */
@Configuration
@Import(FigiProxy::class)
class FigiConfig {
    @Value("\${beancounter.market.providers.figi.key:demo}")
    lateinit var apiKey: String

    @Value("\${beancounter.market.providers.figi.enabled:true}")
    var enabled: Boolean = true

    /**
     * Markets that should use FIGI for asset search (comma-separated).
     * Leave empty to disable FIGI search.
     */
    @Value("\${beancounter.market.providers.figi.search.markets:}")
    var searchMarkets: String = ""

    /**
     * Get the set of markets that should use FIGI for search.
     */
    fun getSearchMarkets(): Set<String> =
        if (searchMarkets.isBlank()) {
            emptySet()
        } else {
            searchMarkets.split(",").map { it.trim().uppercase() }.toSet()
        }

    companion object {
        /**
         * FIGI exchange codes mapped to BC market codes.
         */
        val EXCHANGE_CODES =
            mapOf(
                "US" to "US",
                "ASX" to "AU",
                "NZX" to "NZ",
                "SGX" to "SP"
            )

        /**
         * Get FIGI exchange code for a BC market code.
         */
        fun getExchCode(marketCode: String): String? = EXCHANGE_CODES[marketCode.uppercase()]
    }
}