package com.beancounter.event.integration

import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.TrustedTrnQuery
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Integration calls to svc-position using RestClient.
 */
@Component
class PositionGateway(
    private val positionRestClient: RestClient
) {
    fun query(
        bearerToken: String,
        trnQuery: TrustedTrnQuery
    ): PositionResponse? =
        positionRestClient
            .post()
            .uri("/api/query")
            .header(HttpHeaders.AUTHORIZATION, bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(trnQuery)
            .retrieve()
            .body<PositionResponse>()

    operator fun get(
        bearerToken: String,
        code: String,
        asAt: String,
        value: Boolean = false
    ): PositionResponse =
        positionRestClient
            .get()
            .uri("/api/id/{id}?asAt={asAt}&value={value}", code, asAt, value)
            .header(HttpHeaders.AUTHORIZATION, bearerToken)
            .retrieve()
            .body<PositionResponse>()
            ?: throw BusinessException("Failed to get positions")
}