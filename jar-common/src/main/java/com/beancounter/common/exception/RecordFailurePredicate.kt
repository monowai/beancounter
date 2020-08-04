package com.beancounter.common.exception

import java.util.function.Predicate

/**
 * Test to determine if a business exception has been detected or a system exception.
 * SystemExceptions should be monitored and can trigger Resilience behaviours,
 * business exceptions will not.
 *
 * @author mikeh
 * @since 2019-02-03
 */
class RecordFailurePredicate : Predicate<Throwable?> {
    override fun test(throwable: Throwable?): Boolean {
        return throwable !is BusinessException
    }
}