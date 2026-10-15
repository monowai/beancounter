package com.beancounter.marketdata.apikey

import com.beancounter.auth.AuthConfig
import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.ApiKeyCreatedResponse
import com.beancounter.common.contracts.ApiKeyRequest
import com.beancounter.common.contracts.ApiKeyResponse
import com.beancounter.common.contracts.ApiKeysResponse
import com.beancounter.common.exception.ForbiddenException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Management endpoints for BC-issued API keys (MCP/agent access, phase 1 of
 * bc-claude/MCP.md). Nested under /me - keys belong to the calling
 * SystemUser, alongside the rest of the registration surface. No
 * token-exchange endpoint here - that's phase 2 (and it can't live under
 * /me, since exchange authenticates by the key itself, not a user JWT).
 *
 * Creating and revoking keys needs an account sign-in (an Auth0 token). A
 * token exchanged from an API key may list keys but not change them, so a
 * leaked key cannot mint replacements that outlive its own revocation.
 */
@RestController
@RequestMapping("/me/api-keys")
@CrossOrigin
@PreAuthorize("hasAuthority('" + AuthConstants.SCOPE_USER + "')")
@Tag(
    name = "API Keys",
    description = "Manage BC-issued API keys for programmatic/agent access"
)
class ApiKeyController internal constructor(
    private val apiKeyService: ApiKeyService,
    private val authConfig: AuthConfig
) {
    @PostMapping("")
    @Operation(summary = "Create a new API key - the raw key is returned exactly once")
    fun create(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: ApiKeyRequest
    ): ApiKeyCreatedResponse {
        requireAccountSignIn(jwt)
        return apiKeyService.create(request)
    }

    @GetMapping("")
    @Operation(summary = "List the caller's API keys")
    fun list(): ApiKeysResponse = apiKeyService.list()

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke an API key")
    fun revoke(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: String
    ): ApiKeyResponse {
        requireAccountSignIn(jwt)
        return apiKeyService.revoke(id)
    }

    // The decoder only trusts Auth0 and svc-data's own issuer, so "not ours"
    // means an Auth0 sign-in.
    private fun requireAccountSignIn(jwt: Jwt) {
        if (jwt.getClaimAsString(ISSUER) == authConfig.bcIssuerUri) {
            throw ForbiddenException("API keys can only be managed from an account sign-in")
        }
    }

    private companion object {
        const val ISSUER = "iss"
    }
}