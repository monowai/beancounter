package com.beancounter.marketdata.macro

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.cache.annotation.Cacheable

/**
 * Verify that [EconomicCalendarService.getCalendar] is cached — a miss costs one FRED download per
 * series plus one Kalshi `/markets` call per open event. Mirrors [RateExpectationsCacheTest].
 */
class EconomicCalendarCacheTest {
    @Test
    fun `getCalendar is annotated with Cacheable`() {
        val method = EconomicCalendarService::class.java.getMethod("getCalendar")
        val cacheable = method.getAnnotation(Cacheable::class.java)
        assertThat(cacheable).isNotNull()
        assertThat(cacheable.value).contains(EconomicCalendarService.CACHE_NAME)
    }
}