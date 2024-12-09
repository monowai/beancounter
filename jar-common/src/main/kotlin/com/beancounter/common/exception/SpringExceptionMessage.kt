package com.beancounter.common.exception

import java.util.Date

/**
 * Concrete view of the exception structure we return between services.
 * Base on the Spring exception structure
 * @author mikeh
 * @since 2019-02-04
 */
@Deprecated("Use ProblemDetail")
data class SpringExceptionMessage(
    val timestamp: Date = Date(),
    val error: String?,
    val message: String?,
    val path: String?
)