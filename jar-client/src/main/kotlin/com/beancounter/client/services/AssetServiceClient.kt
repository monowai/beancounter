package com.beancounter.client.services

import com.beancounter.auth.common.TokenService
import com.beancounter.client.AssetService
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Asset
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import javax.annotation.PostConstruct

/**
 * Client side asset services.
 */
@Service
class AssetServiceClient internal constructor(private val assetGateway: AssetGateway, private val tokenService: TokenService) : AssetService {

    @Value("\${marketdata.url:http://localhost:9510/api}")
    private lateinit var marketDataUrl: String

    @PostConstruct
    fun logConfig() {
        log.info("marketdata.url: {}", marketDataUrl)
    }

    override fun handle(assetRequest: AssetRequest): AssetUpdateResponse? {
        return assetGateway.process(tokenService.bearerToken, assetRequest)
    }

    @Async("taskScheduler")
    override fun backFillEvents(assetId: String) {
        // log.debug("Back fill for {}", assetId)
        assetGateway.backFill(tokenService.bearerToken, assetId)
    }

    override fun find(assetId: String): Asset {
        val (data) = assetGateway.find(tokenService.bearerToken, assetId)
            ?: throw BusinessException(String.format("Asset %s not found", assetId))
        return data
    }

    /**
     * Rest API to the asset service.
     */
    @FeignClient(name = "assets", url = "\${marketdata.url:http://localhost:9510/api}")
    interface AssetGateway {
        @PostMapping(value = ["/assets"], produces = [MediaType.APPLICATION_JSON_VALUE], consumes = [MediaType.APPLICATION_JSON_VALUE])
        fun process(
            @RequestHeader("Authorization") bearerToken: String?,
            assetRequest: AssetRequest?
        ): AssetUpdateResponse?

        @PostMapping(value = ["/assets/{id}/events"], produces = [MediaType.APPLICATION_JSON_VALUE], consumes = [MediaType.APPLICATION_JSON_VALUE])
        fun backFill(
            @RequestHeader("Authorization") bearerToken: String?,
            @PathVariable("id") assetId: String?
        )

        @GetMapping(value = ["/assets/{id}"], consumes = [MediaType.APPLICATION_JSON_VALUE])
        fun find(
            @RequestHeader("Authorization") bearerToken: String?,
            @PathVariable("id") assetId: String?
        ): AssetResponse?
    }

    companion object {
        private val log = LoggerFactory.getLogger(AssetServiceClient::class.java)
    }
}
