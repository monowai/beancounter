package com.beancounter.agent.client

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * RestClient for Event MCP service.
 */
@Component
class EventMcpClient(
    @Qualifier("eventMcpRestClient")
    private val restClient: RestClient
) {
    fun ping(): Map<String, String> =
        restClient
            .get()
            .uri("/ping")
            .retrieve()
            .body(object : ParameterizedTypeReference<Map<String, String>>() {})
            ?: emptyMap()

    fun getAssetEvents(assetId: String): Map<String, Any> =
        restClient
            .get()
            .uri("/asset/{assetId}/events", assetId)
            .retrieve()
            .body(object : ParameterizedTypeReference<Map<String, Any>>() {})
            ?: emptyMap()

    fun loadEventsForPortfolio(
        portfolioId: String,
        fromDate: String
    ): Map<String, Any> =
        restClient
            .post()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/portfolio/{portfolioId}/load-events")
                    .queryParam("fromDate", fromDate)
                    .build(portfolioId)
            }.contentType(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(object : ParameterizedTypeReference<Map<String, Any>>() {})
            ?: emptyMap()

    fun backfillEvents(
        portfolioId: String,
        fromDate: String,
        toDate: String? = null
    ): Map<String, Any> =
        restClient
            .post()
            .uri { uriBuilder ->
                val builder =
                    uriBuilder
                        .path("/portfolio/{portfolioId}/backfill")
                        .queryParam("fromDate", fromDate)
                if (toDate != null) {
                    builder.queryParam("toDate", toDate)
                }
                builder.build(portfolioId)
            }.contentType(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(object : ParameterizedTypeReference<Map<String, Any>>() {})
            ?: emptyMap()
}