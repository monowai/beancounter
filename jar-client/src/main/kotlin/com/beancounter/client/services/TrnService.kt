package com.beancounter.client.services

import com.beancounter.auth.TokenService
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Portfolio
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

/**
 * Obtain transactions from the backend.
 */
@Service
class TrnService(
    @Qualifier("bcDataRestClient")
    private val restClient: RestClient,
    private val tokenService: TokenService
) {
    fun write(trnRequest: TrnRequest): TrnResponse =
        restClient
            .post()
            .uri("/api/trns")
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(trnRequest)
            .retrieve()
            .body(TrnResponse::class.java)
            ?: throw BusinessException("Failed to write transactions")

    @CircuitBreaker(name = "default")
    fun query(trustedTrnQuery: TrustedTrnQuery): TrnResponse =
        restClient
            .post()
            .uri("/api/trns/query")
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(trustedTrnQuery)
            .retrieve()
            .body(TrnResponse::class.java)
            ?: throw BusinessException("Failed to query transactions")

    fun query(
        portfolio: Portfolio,
        asAt: String = "today"
    ): TrnResponse =
        restClient
            .get()
            .uri("/api/trns/portfolio/{portfolioId}/{asAt}", portfolio.id, asAt)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(TrnResponse::class.java)
            ?: throw BusinessException("Failed to query portfolio transactions")
}