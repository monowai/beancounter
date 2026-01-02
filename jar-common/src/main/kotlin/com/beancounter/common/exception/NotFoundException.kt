package com.beancounter.common.exception

/**
 * Resource not found - returns HTTP 404.
 * Use this when the requested resource does not exist, as opposed to
 * an invalid URL which would be handled by the framework.
 */
class NotFoundException(
    message: String
) : RuntimeException(message)