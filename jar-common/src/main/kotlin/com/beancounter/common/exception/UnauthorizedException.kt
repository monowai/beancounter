package com.beancounter.common.exception

/**
 * AUTHZ - invalid or no credentials.
 */
class UnauthorizedException(
    message: String?
) : RuntimeException(message)