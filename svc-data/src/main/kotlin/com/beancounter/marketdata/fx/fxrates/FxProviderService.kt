package com.beancounter.marketdata.fx.fxrates

import com.beancounter.common.model.FxRate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Composite FX rate provider that tries configured primary provider first,
 * then falls back to the secondary provider if the primary fails.
 */
@Service
class FxProviderService(
    @Qualifier("frankfurterService")
    private val frankfurterService: FxRateProvider,
    @Qualifier("ecbService")
    private val ecbService: FxRateProvider,
    @Value($$"${beancounter.fx.provider:EXCHANGE_RATES_API}")
    private val defaultProviderId: String
) : FxRateProvider {
    private val log = LoggerFactory.getLogger(FxProviderService::class.java)

    private val providers: Map<String, FxRateProvider> =
        mapOf(
            frankfurterService.id to frankfurterService,
            ecbService.id to ecbService
        )

    private val primaryProvider: FxRateProvider
        get() = providers[defaultProviderId] ?: frankfurterService

    private val fallbackProvider: FxRateProvider
        get() = if (defaultProviderId == frankfurterService.id) ecbService else frankfurterService

    override val id: String = "COMPOSITE"

    /**
     * Get the default provider ID (configured in application.yml).
     */
    fun getDefaultProviderId(): String = defaultProviderId

    /**
     * Get rates using the composite strategy (Frankfurter first, fallback to ECB).
     */
    override fun getRates(asAt: String): List<FxRate> = getRates(asAt, null)

    /**
     * Get rates from a specific provider, or use composite strategy if providerId is null.
     * @param asAt the date to fetch rates for
     * @param providerId specific provider ID (FRANKFURTER, EXCHANGE_RATES_API) or null for composite
     */
    fun getRates(
        asAt: String,
        providerId: String?
    ): List<FxRate> {
        // If specific provider requested, use it directly
        val provider = requestedProvider(providerId)
        return if (provider != null) {
            log.debug("Using specific provider {} for FX rates on {}", providerId, asAt)
            provider.getRates(asAt)
        } else {
            compositeRates(asAt)
        }
    }

    private fun requestedProvider(providerId: String?): FxRateProvider? {
        if (providerId == null) {
            return null
        }
        val provider = providers[providerId]
        if (provider == null) {
            log.warn("Unknown provider {} requested, falling back to composite", providerId)
        }
        return provider
    }

    /**
     * Composite strategy: try primary first, then fallback.
     */
    private fun compositeRates(asAt: String): List<FxRate> {
        val rates = primaryProvider.getRates(asAt)
        if (rates.isNotEmpty()) {
            log.debug("FX rates retrieved from {} for {}", primaryProvider.id, asAt)
            return rates
        }
        return fallbackRates(asAt)
    }

    private fun fallbackRates(asAt: String): List<FxRate> {
        log.info("Falling back to {} for FX rates on {}", fallbackProvider.id, asAt)
        val fallbackRates = fallbackProvider.getRates(asAt)
        return if (fallbackRates.isNotEmpty()) {
            log.debug("FX rates retrieved from {} for {}", fallbackProvider.id, asAt)
            fallbackRates
        } else {
            log.error("Both FX providers failed for date {}", asAt)
            emptyList()
        }
    }

    /**
     * Get available provider IDs for UI selection.
     */
    fun getAvailableProviders(): List<String> = providers.keys.toList()
}