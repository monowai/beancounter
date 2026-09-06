package com.beancounter.marketdata.apikey

import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.common.contracts.ApiKeyTokenRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Exchanges a raw BC API key for a short-lived JWT (bc-claude/MCP.md phase
 * 2). Deliberately unauthenticated - the caller authenticates by presenting
 * the key itself ([ApiKeyService.verify]), not a user JWT, so this can't
 * live under `/me` or behind the usual scope gate (see
 * ControllerAuthorizationTest's allowlist). Guarded instead by
 * [TokenRateLimiter], keyed on caller IP.
 */
@RestController
@RequestMapping("/api-keys")
@Tag(
    name = "API Key Token Exchange",
    description = "Exchange a BC-issued API key for a short-lived JWT"
)
class ApiKeyTokenController(
    private val apiKeyService: ApiKeyService,
    private val bcTokenIssuer: BcTokenIssuer,
    private val tokenRateLimiter: TokenRateLimiter
) {
    @PostMapping("/token")
    @Operation(summary = "Exchange a raw API key for a short-lived JWT")
    fun exchange(
        @RequestBody request: ApiKeyTokenRequest,
        servletRequest: HttpServletRequest
    ): OpenIdResponse {
        tokenRateLimiter.check(servletRequest.remoteAddr)
        val verifiedKey = apiKeyService.verify(request.apiKey)
        val jwt = bcTokenIssuer.mint(verifiedKey)
        return OpenIdResponse(
            token = jwt.tokenValue,
            scope = verifiedKey.scopes,
            expiry = bcTokenIssuer.ttlSeconds,
            type = "Bearer"
        )
    }
}