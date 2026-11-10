package com.beancounter.agent.clients

import com.beancounter.auth.TokenService
import com.beancounter.common.exception.BusinessException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

/**
 * Client for macro market context via svc-data's `/macro` endpoints: treasury yields, oil-proxy
 * moves, Fed rate-decision odds, and the US economic calendar. svc-data owns the upstream
 * integrations; this client returns the raw response bodies untouched, same style as
 * [AlphaVantageNewsClient].
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
            .body(JSON_MAP) ?: emptyMap()

    /**
     * Market-implied Fed rate-decision odds. svc-data's controller returns HTTP 204 No Content
     * (no body) when there's no open Fed event to price. `.toEntity()` — rather than `.body()` —
     * reads the response as a [org.springframework.http.ResponseEntity] whose `.body` is simply
     * null for a 204/empty response, regardless of any missing/mismatched Content-Type header on
     * the no-body response, so this never throws for the no-coverage case; callers treat a null
     * result as no-coverage.
     */
    fun getRateExpectations(): Map<String, Any>? =
        restClient
            .get()
            .uri("/macro/rate-expectations")
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .toEntity(JSON_MAP)
            .body

    /**
     * Latest US payrolls, unemployment and CPI prints and the next scheduled releases. svc-data
     * always returns a full object (empty lists when every leg failed), so a missing body is a
     * contract failure and throws [BusinessException], as the sibling clients do, rather than
     * passing for "no data".
     */
    fun getCalendar(): Map<String, Any> =
        restClient
            .get()
            .uri("/macro/calendar")
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(JSON_MAP)
            ?: throw BusinessException("Failed to retrieve economic calendar")
}