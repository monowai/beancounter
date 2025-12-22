package com.beancounter.agent.config

import com.beancounter.agent.TokenContextService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

/**
 * RestClient interceptor to forward JWT tokens to MCP services.
 * Replaces FeignAuthInterceptor.
 */
class RestClientAuthInterceptor(
    private val tokenContextService: TokenContextService
) : ClientHttpRequestInterceptor {
    private val log = LoggerFactory.getLogger(RestClientAuthInterceptor::class.java)

    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution
    ): ClientHttpResponse {
        try {
            // Skip authentication for ping endpoints (they are designed to be unauthenticated)
            if (request.uri.path.contains("/ping")) {
                log.trace("Skipping authentication for ping endpoint: {}", request.uri)
                return execution.execute(request, body)
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
                    request.headers.setBearerAuth(tokenValue)
                }
                authentication != null && authentication.principal is Jwt -> {
                    val jwt = authentication.principal as Jwt
                    val tokenValue = jwt.tokenValue
                    request.headers.setBearerAuth(tokenValue)
                }
                else -> {
                    // Try fallback to TokenContextService
                    val fallbackToken = tokenContextService.getCurrentToken()
                    if (fallbackToken != null) {
                        log.debug("Using fallback token from TokenContextService for request to: {}", request.uri)
                        request.headers.setBearerAuth(fallbackToken)
                    } else {
                        log.warn(
                            "No JWT token found in security context or TokenContextService for request to: {} (auth: {})",
                            request.uri,
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

        return execution.execute(request, body)
    }
}