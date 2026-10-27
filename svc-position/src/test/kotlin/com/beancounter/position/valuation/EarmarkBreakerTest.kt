package com.beancounter.position.valuation

import com.beancounter.common.exception.ForbiddenException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.exception.SystemException
import com.beancounter.common.exception.UnauthorizedException
import com.beancounter.position.StubbedTest
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * A 4xx from bc-data on the earmark read is the caller's fault (a token with no right to
 * the route, a portfolio that is not there), not a dependency failure. It must not count
 * towards opening the `default` breaker that every bc-data call in svc-position shares:
 * a run of refused earmark reads opened it on kauri and took every bc-data call down with
 * it for the wait window, bc-event's scheduled job included.
 */
@StubbedTest
internal class EarmarkBreakerTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    // The config behind @CircuitBreaker(name = "default") on TrnService.queryProposedCash,
    // on a private instance so the shared breaker's state is left alone.
    private fun breaker(name: String): CircuitBreaker =
        CircuitBreaker.of(name, circuitBreakerRegistry.circuitBreaker("default").circuitBreakerConfig)

    private fun fillWindow(
        breaker: CircuitBreaker,
        error: () -> RuntimeException
    ) = repeat(breaker.circuitBreakerConfig.slidingWindowSize) {
        assertThatThrownBy { breaker.executeRunnable { throw error() } }.isInstanceOf(RuntimeException::class.java)
    }

    @Test
    fun `should not open the breaker when bc-data refuses the earmark read`() {
        val refusals =
            mapOf(
                "forbidden" to { ForbiddenException("widget token may not read proposed cash") },
                "unauthorized" to { UnauthorizedException("token expired") },
                "not-found" to { NotFoundException("portfolio not found") }
            )

        refusals.forEach { (name, error) ->
            val breaker = breaker("earmark-$name")
            fillWindow(breaker, error)
            assertThat(breaker.state).describedAs(name).isEqualTo(CircuitBreaker.State.CLOSED)
            assertThat(breaker.metrics.numberOfFailedCalls).describedAs(name).isZero()
        }
    }

    @Test
    fun `should still open the breaker when bc-data itself fails`() {
        val breaker = breaker("earmark-down")

        fillWindow(breaker) { SystemException("bc-data unavailable") }

        assertThat(breaker.state).isEqualTo(CircuitBreaker.State.OPEN)
    }
}