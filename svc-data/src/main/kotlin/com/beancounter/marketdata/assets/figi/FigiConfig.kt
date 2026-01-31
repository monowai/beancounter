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
         * Reverse mapping: FIGI exchange code to BC market code.
         */
        val REVERSE_EXCHANGE_CODES: Map<String, String> =
            EXCHANGE_CODES.entries.associate { (market, figi) -> figi to market }

        /**
         * Get FIGI exchange code for a BC market code.
         */
        fun getExchCode(marketCode: String): String? = EXCHANGE_CODES[marketCode.uppercase()]

        /**
         * Get BC market code for a FIGI exchange code.
         */
        fun getMarketCode(figiExchCode: String): String? = REVERSE_EXCHANGE_CODES[figiExchCode.uppercase()]
    }
}