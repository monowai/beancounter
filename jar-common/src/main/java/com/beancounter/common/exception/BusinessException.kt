package com.beancounter.common.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * Classification for logic or constraint failures.
 *
 * @author mikeh
 * @since 2019-02-03
 */
@ResponseStatus(code = HttpStatus.BAD_REQUEST)
class BusinessException(message: String?) : RuntimeException(message) 