package com.beancounter.common.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * Every other method on DateUtils reads the clock through the configured zone.
 * `offsetNow` stamped the time-of-day from the JVM default zone instead, which is
 * only correct while the two happen to agree.
 *
 * Kiritimati (UTC+14) and Midway (UTC-11) are 25 hours apart, so the two zones can
 * never report the same time of day and the assertion cannot flake.
 */
class DateUtilsOffsetNowZoneTest {
    private val serviceZone = ZoneId.of("Pacific/Kiritimati")

    @Test
    fun `offsetNow takes its time of day from the configured zone`() {
        val jvmDefault = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Midway"))
        try {
            val dateUtils = DateUtils(serviceZone.id)

            val stamped = dateUtils.offsetNow("2020-11-11").toLocalTime()

            assertThat(Duration.between(stamped, LocalTime.now(serviceZone)).abs())
                .isLessThan(Duration.ofMinutes(1))
        } finally {
            TimeZone.setDefault(jvmDefault)
        }
    }
}