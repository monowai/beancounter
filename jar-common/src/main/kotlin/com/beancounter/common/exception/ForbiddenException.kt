package com.beancounter.common.exception

/**
 * AUTHN - credentials are good, but you have insufficient privileges.
 */
class ForbiddenException(message: String?) : RuntimeException(message)
