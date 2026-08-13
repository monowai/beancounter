package com.beancounter.agent.config

import com.beancounter.common.utils.DateUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Supplies the application [Clock]. Injected into [com.beancounter.agent.AgentController] to stamp
 * the current date onto each user message; a bean (rather than relying on a constructor default)
 * makes the wiring explicit and lets tests substitute a fixed clock.
 *
 * The clock runs in the configured `beancounter.zone` — the same zone the rest of the platform
 * schedules and values against. A UTC clock reports yesterday's date for the opening hours of every
 * local day wherever that zone is east of UTC; the LLM trusts that stamp (the system prompt tells
 * it to) and asks svc-position for the wrong `asAt`, narrating the prior session's price moves as
 * today's.
 */
@Configuration
class TimeConfig {
    @Bean
    fun clock(dateUtils: DateUtils): Clock = Clock.system(dateUtils.zoneId)
}