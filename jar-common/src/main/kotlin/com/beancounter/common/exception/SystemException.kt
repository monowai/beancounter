package com.beancounter.common.exception

/**
 * Classification for integration or other system failures.
 *
 * @author mikeh
 * @since 2019-02-03
 */
class SystemException(reason: String?) : RuntimeException(reason)
