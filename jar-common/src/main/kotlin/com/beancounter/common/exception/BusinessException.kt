package com.beancounter.common.exception

/**
 * Classification for logic or constraint failures.
 *
 * @author mikeh
 * @since 2019-02-03
 */
class BusinessException(
    message: String,
    throwable: Throwable? = null
) : RuntimeException(message, throwable)