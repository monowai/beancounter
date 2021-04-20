package com.beancounter.client.services

import com.beancounter.auth.common.TokenService
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader

/**
 * Client side interface to obtain prices.
 */
@Service
class PriceService @Autowired internal constructor(private val priceGateway: PriceGateway, private val tokenService: TokenService) {
    fun getPrices(priceRequest: PriceRequest): PriceResponse {
        return priceGateway.getPrices(tokenService.bearerToken, priceRequest)
    }

    /**
     * Gateway call to the MarketData service to obtain the prices.
     */
    @FeignClient(name = "prices", url = "\${marketdata.url:http://localhost:9510/api}")
    interface PriceGateway {
        @GetMapping(value = ["/prices"], produces = [MediaType.APPLICATION_JSON_VALUE])
        fun getPrices(
            @RequestHeader("Authorization") bearerToken: String?,
            priceRequest: PriceRequest
        ): PriceResponse
    }
}
