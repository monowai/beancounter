package com.beancounter.marketdata.apikey

import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.common.contracts.ApiKeyTokenRequest
import com.beancounter.marketdata.registration.UserActivityService
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
 * [TokenRateLimiter], keyed on the forwarded client address (falling back
 * to the socket address) - see [TokenRateLimiter.clientKey]. An exchange
 * marks the key's owner as seen: the mobile app and MCP clients reach
 * Beancounter only through exchanged keys, never through `/me`.
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
    private val tokenRateLimiter: TokenRateLimiter,
    private val userActivity: UserActivityService
) {
    @PostMapping("/token")
    @Operation(summary = "Exchange a raw API key, or a device-key assertion, for a short-lived JWT")
    fun exchange(
        @RequestBody request: ApiKeyTokenRequest,
        servletRequest: HttpServletRequest
    ): OpenIdResponse {
        tokenRateLimiter.check(tokenRateLimiter.clientKey(servletRequest))
        val verifiedKey =
            if (request.assertion.isNotBlank()) {
                apiKeyService.verifyDevice(request.assertion)
            } else {
                apiKeyService.verify(request.apiKey)
            }
        val jwt = bcTokenIssuer.mint(verifiedKey)
        userActivity.seenQuietly(verifiedKey.owner.id)
        return OpenIdResponse(
            token = jwt.tokenValue,
            scope = verifiedKey.scopes,
            expiry = bcTokenIssuer.ttlSeconds,
            type = "Bearer"
        )
    }
}