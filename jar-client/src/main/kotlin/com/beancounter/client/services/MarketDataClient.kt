package com.beancounter.client.services

import com.beancounter.auth.TokenService
import com.beancounter.client.Assets
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Asset
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

/**
 * Client side asset services.
 */
@Service
class MarketDataClient(
    @Qualifier("bcDataRestClient")
    private val restClient: RestClient,
    private val tokenService: TokenService
) : Assets {
    @Value("\${marketdata.url:http://localhost:9510}")
    private lateinit var marketDataUrl: String

    @PostConstruct
    fun logConfig() {
        log.info("marketdata.url: {}", marketDataUrl)
    }

    override fun handle(assetRequest: AssetRequest): AssetUpdateResponse? =
        restClient
            .post()
            .uri("/api/assets")
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(assetRequest)
            .retrieve()
            .body(AssetUpdateResponse::class.java)

    @Async("taskScheduler")
    override fun backFillEvents(assetId: String) {
        restClient
            .post()
            .uri("/api/assets/{id}/events", assetId)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .retrieve()
            .toBodilessEntity()
    }

    override fun find(assetId: String): Asset {
        val response =
            restClient
                .get()
                .uri("/api/assets/{id}", assetId)
                .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
                .retrieve()
                .body(AssetResponse::class.java)
                ?: throw BusinessException("Asset not found: $assetId")
        return response.data
    }

    companion object {
        private val log = LoggerFactory.getLogger(MarketDataClient::class.java)
    }
}