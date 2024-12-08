package com.beancounter.common.exception

/**
 * Classification for integration or other system failures.
 *
 * @author mikeh
 * @since 2019-02-03
 */
class SystemException(
    reason: String?,
    throwable: Throwable? = null,
) : RuntimeException(
    reason,
    throwable,
) {
    constructor(reason: String?) : this(
        reason,
        null,
    )
}
