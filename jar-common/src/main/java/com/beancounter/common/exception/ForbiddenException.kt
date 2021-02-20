package com.beancounter.common.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * Basically an HTTP.403.
 */
@ResponseStatus(code = HttpStatus.FORBIDDEN)
class ForbiddenException(message: String?) : RuntimeException(message)
