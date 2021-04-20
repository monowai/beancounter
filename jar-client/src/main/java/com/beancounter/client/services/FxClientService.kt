package com.beancounter.client.services

import com.beancounter.auth.common.TokenService
import com.beancounter.client.FxService
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import org.springframework.cache.annotation.Cacheable
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader

/**
 * Client side calls to the server to obtain FX Rates over a Gateway.
 */
@Service
class FxClientService internal constructor(private val fxGateway: FxGateway, private val tokenService: TokenService) : FxService {
    @Cacheable("fx-request")
    override fun getRates(fxRequest: FxRequest): FxResponse {
        return if (fxRequest.pairs.isEmpty()) {
            FxResponse(FxPairResults())
        } else fxGateway.getRates(tokenService.bearerToken, fxRequest)
    }

    /**
     * Gateway integration call to the backend.
     */
    @FeignClient(name = "fxrates", url = "\${marketdata.url:http://localhost:9510/api}")
    interface FxGateway {
        @PostMapping(value = ["/fx"], produces = [MediaType.APPLICATION_JSON_VALUE], consumes = [MediaType.APPLICATION_JSON_VALUE])
        fun getRates(
            @RequestHeader("Authorization") bearerToken: String?,
            fxRequest: FxRequest
        ): FxResponse
    }
}
