package com.beancounter.agent.config

import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZoneId

/**
 * Pins that the supplied [java.time.Clock] runs in the configured `beancounter.zone`.
 *
 * The agent stamps `[Current date: ...]` onto every user message from this clock and the system
 * prompt tells the LLM to trust it over training data. A UTC clock in an east-of-UTC deployment
 * reports yesterday's date for the opening hours of every local day — the LLM then requests
 * positions for the wrong `asAt` and narrates the prior session's price moves as if they were
 * today's.
 */
internal class TimeConfigTest {
    @Test
    fun `clock bean runs in the configured beancounter zone`() {
        val zone = "Asia/Singapore"

        val clock = TimeConfig().clock(DateUtils(zone))

        assertThat(clock.zone).isEqualTo(ZoneId.of(zone))
    }
}