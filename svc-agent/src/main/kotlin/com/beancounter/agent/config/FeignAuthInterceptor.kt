package com.beancounter.agent.config

import com.beancounter.agent.TokenContextService
import feign.RequestInterceptor
import feign.RequestTemplate
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

/**
 * Feign interceptor to forward JWT tokens to MCP services
 */
@Component
class FeignAuthInterceptor(
    private val tokenContextService: TokenContextService
) : RequestInterceptor {
    private val log = LoggerFactory.getLogger(FeignAuthInterceptor::class.java)

    override fun apply(template: RequestTemplate) {
        try {
            // Skip authentication for ping endpoints (they are designed to be unauthenticated)
            if (template.url().contains("/ping")) {
                log.trace("Skipping authentication for ping endpoint: {}", template.url())
                return
            }

            // Get the current JWT token from the security context
            val authentication = SecurityContextHolder.getContext().authentication
            log.trace(
                "Security context authentication: {} (type: {})",
                authentication?.name,
                authentication?.javaClass?.simpleName
            )

            when {
                authentication is JwtAuthenticationToken -> {
                    val jwt = authentication.token
                    val tokenValue = jwt.tokenValue
                    template.header("Authorization", "Bearer $tokenValue")
                }
                authentication != null && authentication.principal is Jwt -> {
                    val jwt = authentication.principal as Jwt
                    val tokenValue = jwt.tokenValue
                    template.header("Authorization", "Bearer $tokenValue")
                }
                else -> {
                    // Try fallback to TokenContextService
                    val fallbackToken = tokenContextService.getCurrentToken()
                    if (fallbackToken != null) {
                        log.debug("Using fallback token from TokenContextService for request to: {}", template.url())
                        template.header("Authorization", "Bearer $fallbackToken")
                    } else {
                        log.warn(
                            "No JWT token found in security context or TokenContextService for request to: {} (auth: {})",
                            template.url(),
                            authentication?.javaClass?.simpleName ?: "null"
                        )
                        // Don't add Authorization header - let the downstream service handle the missing token
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Error forwarding JWT token to MCP service: {}", e.message, e)
            // Don't add Authorization header on error
        }
    }
}