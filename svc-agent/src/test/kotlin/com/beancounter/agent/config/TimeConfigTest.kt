package com.beancounter.agent.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

/**
 * Pins that the supplied [java.time.Clock] is UTC — the agent stamps message dates from it, so a
 * locally-zoned clock would drift the "current date" off the user's expectation near midnight.
 */
internal class TimeConfigTest {
    @Test
    fun `clock bean is UTC`() {
        val clock = TimeConfig().clock()

        assertThat(clock.zone).isEqualTo(ZoneOffset.UTC)
    }
}