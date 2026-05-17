package com.beancounter.client.services

import com.beancounter.auth.TokenService
import com.beancounter.common.contracts.BulkPriceRequest
import com.beancounter.common.contracts.BulkPriceResponse
import com.beancounter.common.contracts.EnsureHistoryRequest
import com.beancounter.common.contracts.EnsureHistoryResponse
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

    /**
             * Request bulk prices for multiple assets using the provided request payload.
             *
             * @param bulkPriceRequest The request payload specifying the assets and parameters for the bulk price lookup.
             * @param token Authorization bearer token to include in the request `Authorization` header.
             * @return The `BulkPriceResponse` parsed from the service response.
             * @throws BusinessException if the response body is null and bulk prices could not be obtained.
             */
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

    /**
             * Requests scheduling of historical price data operations for the provided assets.
             *
             * @param ensureHistoryRequest Details of the assets and time range to ensure history for.
             * @param token Bearer token used for the Authorization header; defaults to the token from the injected tokenService.
             * @return The service's response mapped to `EnsureHistoryResponse`.
             * @throws BusinessException if the remote service returns no body and the response cannot be parsed.
             */
            fun ensureHistory(
        ensureHistoryRequest: EnsureHistoryRequest,
        token: String = tokenService.bearerToken
    ): EnsureHistoryResponse =
        restClient
            .post()
            .uri("/prices/ensure-history")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ensureHistoryRequest)
            .retrieve()
            .body(EnsureHistoryResponse::class.java)
            ?: throw BusinessException("Failed to schedule ensure-history")

    /**
             * Fetches price events for the given asset.
             *
             * @param assetId The asset identifier substituted into the request path.
             * @return The parsed PriceResponse containing price events for the specified asset.
             * @throws BusinessException if the response body cannot be parsed into a PriceResponse.
             */
            fun getEvents(assetId: String): PriceResponse =
        restClient
            .get()
            .uri("/prices/{assetId}/events", assetId)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(PriceResponse::class.java)
            ?: throw BusinessException("Failed to get price events")
}