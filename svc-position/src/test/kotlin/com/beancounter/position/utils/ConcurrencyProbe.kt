package com.beancounter.position.utils

import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Proves that stubbed calls overlap. Each [arrive] waits until every party has
 * arrived, so calls made one after another time out and [allMet] is false.
 * Also records the authentication bound to the thread each call ran on.
 */
class ConcurrencyProbe(
    private val parties: Int,
    private val timeoutMillis: Long = 2_000
) {
    private val latch = CountDownLatch(parties)
    private val met: MutableList<Boolean> = Collections.synchronizedList(mutableListOf())
    val callers: MutableList<Authentication?> = Collections.synchronizedList(mutableListOf())

    fun <T> arrive(result: T): T {
        callers.add(SecurityContextHolder.getContext().authentication)
        latch.countDown()
        met.add(latch.await(timeoutMillis, TimeUnit.MILLISECONDS))
        return result
    }

    val allMet: Boolean
        get() = met.size == parties && met.all { it }
}