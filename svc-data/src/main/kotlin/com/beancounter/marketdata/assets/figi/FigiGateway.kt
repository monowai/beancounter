package com.beancounter.marketdata.assets.figi

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * API calls to OpenFIGI using RestClient.
 */
@Component
class FigiGateway(
    @Qualifier("openFigiRestClient")
    private val restClient: RestClient
) {
    fun search(
        searchBody: Collection<FigiSearch>,
        apiKey: String
    ): Collection<FigiResponse> =
        restClient
            .post()
            .uri("/v2/mapping")
            .header("X-OPENFIGI-APIKEY", apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(searchBody)
            .retrieve()
            .body(object : ParameterizedTypeReference<Collection<FigiResponse>>() {})
            ?: emptyList()
}