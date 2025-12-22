package com.beancounter.agent.client

import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Portfolio
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * RestClient for Position MCP service.
 */
@Component
class PositionMcpClient(
    @Qualifier("positionMcpRestClient")
    private val restClient: RestClient
) {
    fun ping(): Map<String, String> =
        restClient
            .get()
            .uri("/ping")
            .retrieve()
            .body(object : ParameterizedTypeReference<Map<String, String>>() {})
            ?: emptyMap()

    fun getPortfolioPositions(
        portfolio: Portfolio,
        valuationDate: String
    ): PositionResponse =
        restClient
            .post()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/portfolio/positions")
                    .queryParam("valuationDate", valuationDate)
                    .build()
            }.contentType(MediaType.APPLICATION_JSON)
            .body(portfolio)
            .retrieve()
            .body(PositionResponse::class.java)
            ?: throw BusinessException("Failed to get portfolio positions")

    fun queryPositions(query: TrustedTrnQuery): PositionResponse =
        restClient
            .post()
            .uri("/query")
            .contentType(MediaType.APPLICATION_JSON)
            .body(query)
            .retrieve()
            .body(PositionResponse::class.java)
            ?: throw BusinessException("Failed to query positions")

    fun buildPositions(
        portfolio: Portfolio,
        valuationDate: String
    ): PositionResponse =
        restClient
            .post()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/portfolio/build")
                    .queryParam("valuationDate", valuationDate)
                    .build()
            }.contentType(MediaType.APPLICATION_JSON)
            .body(portfolio)
            .retrieve()
            .body(PositionResponse::class.java)
            ?: throw BusinessException("Failed to build positions")

    fun valuePositions(positionResponse: PositionResponse): PositionResponse =
        restClient
            .post()
            .uri("/value")
            .contentType(MediaType.APPLICATION_JSON)
            .body(positionResponse)
            .retrieve()
            .body(PositionResponse::class.java)
            ?: throw BusinessException("Failed to value positions")

    fun getPortfolioMetrics(
        portfolio: Portfolio,
        valuationDate: String
    ): Map<String, Any> =
        restClient
            .post()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/portfolio/metrics")
                    .queryParam("valuationDate", valuationDate)
                    .build()
            }.contentType(MediaType.APPLICATION_JSON)
            .body(portfolio)
            .retrieve()
            .body(object : ParameterizedTypeReference<Map<String, Any>>() {})
            ?: emptyMap()

    fun getPositionBreakdown(
        portfolio: Portfolio,
        valuationDate: String
    ): Map<String, Any> =
        restClient
            .post()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/portfolio/breakdown")
                    .queryParam("valuationDate", valuationDate)
                    .build()
            }.contentType(MediaType.APPLICATION_JSON)
            .body(portfolio)
            .retrieve()
            .body(object : ParameterizedTypeReference<Map<String, Any>>() {})
            ?: emptyMap()
}