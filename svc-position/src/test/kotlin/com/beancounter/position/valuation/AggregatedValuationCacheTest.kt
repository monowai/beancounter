package com.beancounter.position.valuation

import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.utils.TestHelpers
import com.github.benmanes.caffeine.cache.Ticker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * TDD spec for the short-TTL, single-flight cache in front of
 * [ValuationService.getAggregatedPositions]. Constructs [AggregatedValuationCache]
 * directly with a counting/fake loader rather than standing up a Spring
 * context - the cache's correctness (single-flight, per-key isolation, TTL,
 * bypass) is independent of how ValuationService builds its key.
 */
class AggregatedValuationCacheTest {
    private val portfolio = TestHelpers.createTestPortfolio("AggregatedValuationCacheTest")

    private fun testKey(subject: String = "subject-1") =
        ValuationCacheKey.of(
            subject = subject,
            portfolios = listOf(portfolio),
            valuationDate = DateUtils.TODAY,
            value = true,
            targetCurrencyCode = null
        )

    @Test
    fun `should collapse concurrent identical valuations into one load`() {
        val threadCount = 8
        val cache = AggregatedValuationCache(ttlSeconds = 10)
        val key = testKey()
        val response = PositionResponse()
        val callCount = AtomicInteger(0)
        val readyLatch = CountDownLatch(threadCount)
        val startLatch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threadCount)

        val futures =
            (1..threadCount).map {
                executor.submit<PositionResponse> {
                    readyLatch.countDown()
                    startLatch.await()
                    cache.get(key) {
                        callCount.incrementAndGet()
                        // Hold the load open so every thread arrives while it's in flight.
                        Thread.sleep(100)
                        response
                    }
                }
            }
        readyLatch.await()
        startLatch.countDown()
        val results = futures.map { it.get(10, TimeUnit.SECONDS) }
        executor.shutdown()

        assertThat(callCount.get())
            .describedAs("loader must run exactly once for $threadCount concurrent identical calls")
            .isEqualTo(1)
        assertThat(results).allMatch { it === response }
    }

    @Test
    fun `should not share results across users`() {
        val cache = AggregatedValuationCache(ttlSeconds = 10)
        val callCount = AtomicInteger(0)

        val resultA =
            cache.get(testKey(subject = "subject-a")) {
                callCount.incrementAndGet()
                PositionResponse()
            }
        val resultB =
            cache.get(testKey(subject = "subject-b")) {
                callCount.incrementAndGet()
                PositionResponse()
            }

        assertThat(callCount.get())
            .describedAs("different subjects with identical portfolio ids must both miss")
            .isEqualTo(2)
        assertThat(resultA).isNotSameAs(resultB)
    }

    @Test
    fun `should expire after ttl`() {
        val ticker = FakeTicker()
        val cache = AggregatedValuationCache(ttlSeconds = 1, ticker = ticker)
        val key = testKey()
        val callCount = AtomicInteger(0)

        cache.get(key) {
            callCount.incrementAndGet()
            PositionResponse()
        }
        ticker.advanceSeconds(2)
        cache.get(key) {
            callCount.incrementAndGet()
            PositionResponse()
        }

        assertThat(callCount.get())
            .describedAs("loader must re-run once the entry is older than the TTL")
            .isEqualTo(2)
    }

    @Test
    fun `should bypass cache when ttl is zero`() {
        val cache = AggregatedValuationCache(ttlSeconds = 0)
        val key = testKey()
        val callCount = AtomicInteger(0)

        cache.get(key) {
            callCount.incrementAndGet()
            PositionResponse()
        }
        cache.get(key) {
            callCount.incrementAndGet()
            PositionResponse()
        }

        assertThat(callCount.get())
            .describedAs("ttl=0 must bypass the cache entirely")
            .isEqualTo(2)
    }

    /**
     * Deterministic [Ticker] so TTL expiry can be asserted without a real sleep.
     */
    private class FakeTicker : Ticker {
        private var nanos = 0L

        fun advanceSeconds(seconds: Long) {
            nanos += TimeUnit.SECONDS.toNanos(seconds)
        }

        override fun read(): Long = nanos
    }
}