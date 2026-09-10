package com.beancounter.agent.clients

import com.beancounter.auth.TokenService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

/**
 * Client for macro market context via svc-data's `/macro` endpoints: treasury yields, oil-proxy
 * moves, and Fed rate-decision odds. svc-data owns the upstream integrations; this client returns
 * the raw response bodies untouched, same style as [AlphaVantageNewsClient].
 */
@Service
class MacroClient(
    @Qualifier("bcDataRestClient")
    private val restClient: RestClient,
    private val tokenService: TokenService
) {
    /**
     * Treasury yield levels + change in bps, and oil-proxy % moves, over [lookbackDays].
     * An empty response body (both yields and oil failed upstream) maps to an empty map.
     */
    fun getIndicators(lookbackDays: Int): Map<String, Any> =
        restClient
            .get()
            .uri("/macro/indicators?lookbackDays={lookbackDays}", mapOf("lookbackDays" to lookbackDays))
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(MAP_TYPE) ?: emptyMap()

    /**
     * Market-implied Fed rate-decision odds. svc-data's controller return type is nullable and
     * serializes to an EMPTY response body — not a JSON `null` — when there's no open Fed event
     * to price. [RestClient] surfaces an empty body as a null return from `.body()`, which this
     * method passes straight through so callers can treat it as no-coverage.
     */
    fun getRateExpectations(): Map<String, Any>? =
        restClient
            .get()
            .uri("/macro/rate-expectations")
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(MAP_TYPE)

    companion object {
        private val MAP_TYPE = object : ParameterizedTypeReference<Map<String, Any>>() {}
    }
}