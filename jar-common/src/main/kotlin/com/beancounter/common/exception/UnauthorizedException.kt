package com.beancounter.common.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * AUTHZ - invalid or no credentials.
 */
@ResponseStatus(code = HttpStatus.UNAUTHORIZED)
class UnauthorizedException(message: String?) : RuntimeException(message)
