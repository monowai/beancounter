package com.beancounter.position.utils

import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder

/**
 * Runs [block] as the caller on a dispatched thread. The security context is
 * thread-local, so a coroutine on `Dispatchers.IO` has none until it is bound;
 * bc-data clients read the caller's token from it. Clears it afterwards so the
 * pooled thread carries no identity into its next task.
 */
fun <T> SecurityContext.runAs(block: () -> T): T {
    SecurityContextHolder.setContext(this)
    try {
        return block()
    } finally {
        SecurityContextHolder.clearContext()
    }
}