package com.beancounter.common.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * AUTHN - credentials are good, but you have insufficient privileges.
 */
@ResponseStatus(code = HttpStatus.FORBIDDEN)
class ForbiddenException(message: String?) : RuntimeException(message)
