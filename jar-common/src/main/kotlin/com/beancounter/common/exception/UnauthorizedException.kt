package com.beancounter.common.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(code = HttpStatus.UNAUTHORIZED)
/**
 * AUTHZ - invalid or no credentials.
 */
class UnauthorizedException(message: String?) : RuntimeException(message)
