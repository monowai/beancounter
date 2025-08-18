package com.beancounter.event.config

import com.beancounter.auth.TokenService
import com.beancounter.event.service.EventService
import com.beancounter.event.service.PositionService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration for BackFillService dependencies.
 */
data class BackFillServiceConfig(
    val sharedConfig: SharedConfig,
    val positionService: PositionService,
    val eventService: EventService,
    val tokenService: TokenService
)

@Configuration
class BackFillServiceConfiguration {
    @Bean
    fun backFillServiceConfig(
        sharedConfig: SharedConfig,
        positionService: PositionService,
        eventService: EventService,
        tokenService: TokenService
    ): BackFillServiceConfig =
        BackFillServiceConfig(
            sharedConfig = sharedConfig,
            positionService = positionService,
            eventService = eventService,
            tokenService = tokenService
        )
}