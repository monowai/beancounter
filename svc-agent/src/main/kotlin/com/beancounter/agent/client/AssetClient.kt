package com.beancounter.agent.client

import com.beancounter.auth.TokenService
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.model.Asset
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Thin client for the svc-data assets API. Resolves a (market, ticker)
 * pair to a Beancounter [Asset] (creating one on demand). Used by the
 * agent's by-ticker tools (e.g. dividend / split history) so the LLM can
 * answer questions like "how many dividends has GOOG paid?" without ever
 * seeing internal asset ids.
 */
@Service
class AssetClient(
    @Qualifier("bcDataRestClient")
    private val restClient: RestClient,
    private val tokenService: TokenService
) {
    fun getAsset(
        market: String,
        code: String
    ): Asset {
        // ?canonical=true asks svc-data to fold colloquial inputs
        // (NASDAQ/NYSE/AMEX/ARCA, NASAQ, DOW, DOW JONES) onto the active
        // US market before lookup — so the LLM doesn't need to know BC's
        // exchange conventions and we don't mint a duplicate per-exchange
        // asset row.
        val response =
            restClient
                .get()
                .uri("/assets/{market}/{code}?canonical=true", market, code)
                .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
                .retrieve()
                .body<AssetResponse>()
        return response?.data
            ?: error("Unable to resolve asset $market:$code")
    }
}