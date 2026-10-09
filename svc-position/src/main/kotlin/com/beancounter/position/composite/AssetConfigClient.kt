package com.beancounter.position.composite

import com.beancounter.auth.TokenService
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.body

/**
 * Read-only client for svc-data's PrivateAssetConfig endpoint. Used by
 * composite valuation to source sub-account balances that aren't held in
 * svc-position's transaction-driven positions.
 */
@Service
class AssetConfigClient(
    @Qualifier("bcDataRestClient")
    private val restClient: RestClient,
    private val tokenService: TokenService
) {
    fun find(assetId: String): PrivateAssetConfigDto {
        val response =
            try {
                restClient
                    .get()
                    .uri("/assets/config/{id}", assetId)
                    .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
                    .retrieve()
                    .body<PrivateAssetConfigResponseDto>()
            } catch (ex: RestClientResponseException) {
                if (ex.statusCode.value() == 404) {
                    throw NotFoundException("Asset config not found: $assetId")
                }
                throw ex
            } ?: throw NotFoundException("Asset config not found: $assetId")
        return response.data
    }

    /**
     * Every private-asset config visible to the caller. Used by the net-worth
     * rollup to find composite (sub-account) balances that have no parent
     * position in any portfolio.
     */
    fun findAll(): List<PrivateAssetConfigDto> {
        val response =
            restClient
                .get()
                .uri("/assets/config")
                .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
                .retrieve()
                .body<PrivateAssetConfigsResponseDto>()
                ?: throw BusinessException("svc-data returned no body for /assets/config")
        return response.data
    }
}