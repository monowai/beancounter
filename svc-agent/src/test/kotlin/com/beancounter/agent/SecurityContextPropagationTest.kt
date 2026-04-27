package com.beancounter.agent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.util.concurrent.atomic.AtomicReference

/**
 * Regression test for the bug observed on the new `/agent/query/stream`
 * endpoint where `TokenService.jwt` (which reads
 * `SecurityContextHolder.getContext().authentication`) returns null because
 * Spring AI's `ChatClient.stream()` runs tool callbacks on a Reactor scheduler
 * thread — *not* the original request thread.
 *
 * Reproduces the threading model in isolation:
 *   1. Populate SecurityContextHolder on the calling thread.
 *   2. Run a Flux on Schedulers.parallel() (the same family of pool that
 *      backs Reactor's IO operators that Spring AI uses internally).
 *   3. Read SecurityContextHolder from inside that operator.
 *
 * Without context propagation enabled, step 3 sees `null`.
 * With the configuration introduced by [SecurityContextPropagationConfig]
 * + `.contextCapture()`, the original Authentication is restored.
 */
class SecurityContextPropagationTest {
    companion object {
        // Reactor's automatic-context-propagation hook and the
        // ContextRegistry accessor are global, process-wide state. Enable
        // them exactly once for the whole test class — invoking the same
        // setup per-test would re-register the accessor on every run, which
        // is wasted work at best and a state-leak at worst when JUnit
        // parallelises classes that touch the same ContextRegistry.
        @BeforeAll
        @JvmStatic
        fun enableContextPropagationOnce() {
            SecurityContextPropagationConfig().enablePropagation()
        }
    }

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `Authentication survives Reactor parallel-scheduler hop with contextCapture`() {
        val original: Authentication =
            TestingAuthenticationToken("user@example.com", "creds", "ROLE_USER")
        SecurityContextHolder.getContext().authentication = original

        val captured = AtomicReference<Authentication?>()
        Flux
            .just(1)
            .publishOn(Schedulers.parallel())
            .doOnNext {
                captured.set(SecurityContextHolder.getContext().authentication)
            }.contextCapture()
            .blockLast()

        assertThat(captured.get())
            .`as`(
                "SecurityContext must travel from the request thread into Reactor " +
                    "scheduler threads, otherwise tool callbacks during ChatClient.stream() " +
                    "see an empty SecurityContextHolder and TokenService.jwt throws."
            ).isSameAs(original)
    }
}