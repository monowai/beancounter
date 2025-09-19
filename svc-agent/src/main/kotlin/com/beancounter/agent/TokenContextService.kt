package com.beancounter.agent

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service

/**
 * Service to manage JWT token context across thread boundaries.
 * Provides explicit token capture and retrieval for Feign clients.
 */
@Service
class TokenContextService {
    private val tokenThreadLocal = ThreadLocal<String>()

    /**
     * Capture the current JWT token from SecurityContext and store it in ThreadLocal
     */
    fun captureCurrentToken(): String? {
        val token =
            when (val authentication = SecurityContextHolder.getContext().authentication) {
                is JwtAuthenticationToken -> authentication.token.tokenValue
                else -> null
            }

        if (token != null) {
            tokenThreadLocal.set(token)
        }

        return token
    }

    /**
     * Get the stored JWT token from ThreadLocal
     */
    fun getCurrentToken(): String? = tokenThreadLocal.get()

    /**
     * Clear the stored JWT token from ThreadLocal
     */
    fun clearToken() {
        tokenThreadLocal.remove()
    }

    /**
     * Execute a block with the current token captured and automatically clear afterwards
     */
    fun <T> withCurrentToken(block: () -> T): T {
        captureCurrentToken()
        try {
            return block()
        } finally {
            clearToken()
        }
    }
}