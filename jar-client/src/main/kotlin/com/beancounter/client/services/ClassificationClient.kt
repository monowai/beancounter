package com.beancounter.client.services

import com.beancounter.auth.TokenService
import com.beancounter.common.contracts.BulkClassificationRequest
import com.beancounter.common.contracts.BulkClassificationResponse
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body

/**
 * Client for fetching asset classifications from svc-data.
 */
@Service
class ClassificationClient(
    @Qualifier("bcDataRestClient")
    private val restClient: RestClient,
    private val tokenService: TokenService
) {
    companion object {
        private val log = LoggerFactory.getLogger(ClassificationClient::class.java)
    }

    /**
     * Fetches bulk classifications for the given asset IDs.
     * Returns empty map on failure to avoid blocking position requests.
     */
    @Retry(name = "data")
    fun getClassifications(assetIds: List<String>): BulkClassificationResponse {
        if (assetIds.isEmpty()) {
            return BulkClassificationResponse()
        }

        return try {
            restClient
                .post()
                .uri("/classifications/bulk")
                .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BulkClassificationRequest(assetIds))
                .retrieve()
                .body<BulkClassificationResponse>()
                ?: BulkClassificationResponse()
        } catch (e: RestClientException) {
            log.warn("Failed to fetch classifications: ${e.message}")
            BulkClassificationResponse()
        }
    }
}