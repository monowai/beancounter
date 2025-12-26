package com.beancounter.client.services

import com.beancounter.client.FxService
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.exception.BusinessException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Client side calls to the server to obtain FX Rates over a Gateway.
 */
@Service
class FxClientService(
    @Qualifier("bcDataRestClient")
    private val restClient: RestClient
) : FxService {
    @Cacheable("fx-request")
    override fun getRates(
        fxRequest: FxRequest,
        token: String
    ): FxResponse =
        if (fxRequest.pairs.isEmpty()) {
            FxResponse(FxPairResults())
        } else {
            restClient
                .post()
                .uri("/api/fx")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(fxRequest)
                .retrieve()
                .body<FxResponse>()
                ?: throw BusinessException("Failed to get FX rates")
        }
}