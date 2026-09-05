package com.beancounter.marketdata.apikey

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Publishes the public half of svc-data's own token-issuance signing key
 * (bc-claude/MCP.md phase 2). Deliberately unauthenticated - JWKS documents
 * are public key material by design, the same way Auth0's own
 * `.well-known/jwks.json` is public (see ControllerAuthorizationTest's
 * allowlist for the full rationale).
 */
@RestController
@Tag(
    name = "JWKS",
    description = "Public signing keys for JWTs issued by this service"
)
class JwksController(
    private val bcTokenIssuer: BcTokenIssuer
) {
    @GetMapping("/.well-known/jwks.json")
    @Operation(summary = "Public JWK set for tokens minted by the /api-keys/token endpoint")
    fun jwks(): Map<String, Any> = bcTokenIssuer.publicJwks()
}