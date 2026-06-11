package com.beancounter.position.schedule

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
class PortfolioRevaluationTriggerTest {
    @Mock
    private lateinit var revaluator: PortfolioRevaluator

    private val scheduler =
        ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadNamePrefix("revaluation-trigger-test-")
            setRemoveOnCancelPolicy(true)
            initialize()
        }

    @Test
    fun `single event runs revaluation after debounce window elapses`() {
        val latch = CountDownLatch(1)
        whenever(revaluator.revalueAll(any())).thenAnswer {
            latch.countDown()
            PortfolioRevaluator.Result.completed(1, 0)
        }
        val trigger =
            PortfolioRevaluationTrigger(
                revaluator = revaluator,
                debounce = Duration.ofMillis(150),
                taskScheduler = scheduler
            )

        trigger.scheduleRevaluation(reason = "test")

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue()
        verify(revaluator, times(1)).revalueAll(eq("test"))
    }

    @Test
    fun `burst of events collapses to a single revaluation`() {
        val latch = CountDownLatch(1)
        whenever(revaluator.revalueAll(any())).thenAnswer {
            latch.countDown()
            PortfolioRevaluator.Result.completed(1, 0)
        }
        val trigger =
            PortfolioRevaluationTrigger(
                revaluator = revaluator,
                debounce = Duration.ofMillis(200),
                taskScheduler = scheduler
            )

        repeat(5) { trigger.scheduleRevaluation(reason = "burst") }

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue()
        // No second invocation in the lifetime of this test, which is
        // bounded by the latch.await window above (~200 ms post-fire).
        verify(revaluator, times(1)).revalueAll(any())
    }

    @Test
    fun `early cancellation prevents revaluation`() {
        val trigger =
            PortfolioRevaluationTrigger(
                revaluator = revaluator,
                debounce = Duration.ofSeconds(5),
                taskScheduler = scheduler
            )

        trigger.scheduleRevaluation(reason = "to-cancel")
        val pending = trigger.pendingTask()
        checkNotNull(pending)
        assertThat(pending.cancel(false)).isTrue()

        // No revaluation should fire within the inflate window (1 s « 5 s debounce).
        Thread.sleep(1_000)
        verify(revaluator, never()).revalueAll(any())
    }
}