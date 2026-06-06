package com.beancounter.agent.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Supplies the application [Clock]. Injected into [com.beancounter.agent.AgentController] to stamp
 * the current date onto each user message; a bean (rather than relying on a constructor default)
 * makes the wiring explicit and lets tests substitute a fixed clock.
 */
@Configuration
class TimeConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}