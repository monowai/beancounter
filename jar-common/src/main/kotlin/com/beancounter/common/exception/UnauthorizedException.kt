package com.beancounter.common.exception

/**
 * AUTHZ - invalid or no credentials.
 */
class UnauthorizedException(
    message: String?,
    cause: Throwable? = null
) : RuntimeException(message, cause)