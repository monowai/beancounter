package com.beancounter.client.services

import com.beancounter.auth.TokenService
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Portfolio
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader

/**
 * Obtain transactions from the backend.
 */
@Service
class TrnService internal constructor(
    private val trnGateway: TrnGateway,
    private val tokenService: TokenService
) {
    fun write(trnRequest: TrnRequest): TrnResponse {
        return trnGateway.write(tokenService.bearerToken, trnRequest)
    }

    @CircuitBreaker(name = "default")
    fun query(trustedTrnQuery: TrustedTrnQuery): TrnResponse {
        return trnGateway.read(tokenService.bearerToken, trustedTrnQuery)
    }

    fun query(portfolio: Portfolio): TrnResponse {
        return trnGateway.read(tokenService.bearerToken, portfolio.id)
    }

    /**
     * GatewayProxy to talk to svc-data and obtain the transactions.
     */
    @FeignClient(name = "trns", url = "\${marketdata.url:http://localhost:9510/api}")
    interface TrnGateway {
        @PostMapping(
            value = ["/trns"],
            produces = [MediaType.APPLICATION_JSON_VALUE],
            consumes = [MediaType.APPLICATION_JSON_VALUE]
        )
        fun write(
            @RequestHeader("Authorization") bearerToken: String,
            trnRequest: TrnRequest
        ): TrnResponse

        @GetMapping(
            value = ["/trns/portfolio/{portfolioId}"],
            produces = [MediaType.APPLICATION_JSON_VALUE]
        )
        fun read(
            @RequestHeader("Authorization") bearerToken: String,
            @PathVariable("portfolioId") portfolioId: String
        ): TrnResponse

        @PostMapping(
            value = ["/trns/query"],
            produces = [MediaType.APPLICATION_JSON_VALUE],
            consumes = [MediaType.APPLICATION_JSON_VALUE]
        )
        fun read(
            @RequestHeader("Authorization") bearerToken: String,
            trnQuery: TrustedTrnQuery
        ): TrnResponse
    }
}
