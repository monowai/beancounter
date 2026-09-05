package com.beancounter.marketdata.apikey

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.ApiKeyCreatedResponse
import com.beancounter.common.contracts.ApiKeyRequest
import com.beancounter.common.contracts.ApiKeyResponse
import com.beancounter.common.contracts.ApiKeysResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
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
    private val apiKeyService: ApiKeyService
) {
    @PostMapping("")
    @Operation(summary = "Create a new API key - the raw key is returned exactly once")
    fun create(
        @RequestBody request: ApiKeyRequest
    ): ApiKeyCreatedResponse = apiKeyService.create(request)

    @GetMapping("")
    @Operation(summary = "List the caller's API keys")
    fun list(): ApiKeysResponse = apiKeyService.list()

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke an API key")
    fun revoke(
        @PathVariable id: String
    ): ApiKeyResponse = apiKeyService.revoke(id)
}