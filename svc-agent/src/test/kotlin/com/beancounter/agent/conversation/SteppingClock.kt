package com.beancounter.agent.conversation

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock the test can move forward, so idle-age behaviour is observable. */
class SteppingClock(
    private var now: Instant
) : Clock() {
    fun advance(duration: Duration) {
        now = now.plus(duration)
    }

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now
}

/** The [SteppingClock] the conversation tests share, starting at a fixed instant. */
@TestConfiguration
class SteppingClockConfig {
    @Bean
    fun clock(): SteppingClock = SteppingClock(Instant.parse("2026-10-01T00:00:00Z"))
}