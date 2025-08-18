package com.beancounter.event.config

import com.beancounter.event.service.BackFillService
import com.beancounter.event.service.EventLoader
import com.beancounter.event.service.EventService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration for EventController dependencies.
 */
data class EventControllerConfig(
    val serviceConfig: EventControllerServiceConfig,
    val sharedConfig: SharedConfig
)

data class EventControllerServiceConfig(
    val eventService: EventService,
    val backFillService: BackFillService,
    val eventLoader: EventLoader
)

@Configuration
class EventControllerConfiguration {
    @Bean
    fun eventControllerServiceConfig(
        eventService: EventService,
        backFillService: BackFillService,
        eventLoader: EventLoader
    ): EventControllerServiceConfig =
        EventControllerServiceConfig(
            eventService = eventService,
            backFillService = backFillService,
            eventLoader = eventLoader
        )

    @Bean
    fun eventControllerConfig(
        serviceConfig: EventControllerServiceConfig,
        sharedConfig: SharedConfig
    ): EventControllerConfig =
        EventControllerConfig(
            serviceConfig = serviceConfig,
            sharedConfig = sharedConfig
        )
}