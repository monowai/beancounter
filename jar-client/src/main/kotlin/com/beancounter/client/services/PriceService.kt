package com.beancounter.client.services

import com.beancounter.auth.TokenService
import com.beancounter.common.contracts.BulkPriceRequest
import com.beancounter.common.contracts.BulkPriceResponse
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.exception.BusinessException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

/**
 * Client side interface to obtain prices.
 */
@Service
class PriceService(
    @Qualifier("bcDataRestClient")
    private val restClient: RestClient,
    private val tokenService: TokenService
) {
    fun getPrices(
        priceRequest: PriceRequest,
        token: String = tokenService.bearerToken
    ): PriceResponse =
        restClient
            .post()
            .uri("/prices")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(priceRequest)
            .retrieve()
            .body(PriceResponse::class.java)
            ?: throw BusinessException("Failed to get prices")

    fun getBulkPrices(
        bulkPriceRequest: BulkPriceRequest,
        token: String = tokenService.bearerToken
    ): BulkPriceResponse =
        restClient
            .post()
            .uri("/prices/bulk")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(bulkPriceRequest)
            .retrieve()
            .body(BulkPriceResponse::class.java)
            ?: throw BusinessException("Failed to get bulk prices")

    fun getEvents(assetId: String): PriceResponse =
        restClient
            .get()
            .uri("/prices/{assetId}/events", assetId)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(PriceResponse::class.java)
            ?: throw BusinessException("Failed to get price events")
}