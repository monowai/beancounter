package com.beancounter.event.config

import com.beancounter.auth.client.LoginService
import com.beancounter.client.services.PriceService
import com.beancounter.event.common.DateSplitter
import com.beancounter.event.service.BackFillService
import com.beancounter.event.service.EventService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration for EventLoader dependencies.
 */
data class EventLoaderConfig(
    val sharedConfig: SharedConfig,
    val serviceConfig: EventLoaderServiceConfig,
    val authConfig: AuthConfig
)

data class EventLoaderServiceConfig(
    val eventService: EventService,
    val priceService: PriceService,
    val backFillService: BackFillService,
    val positionService: com.beancounter.event.service.PositionService
)

data class AuthConfig(
    val dateSplitter: DateSplitter,
    val loginService: LoginService
)

@Configuration
class EventLoaderConfiguration {
    @Bean
    fun eventLoaderServiceConfig(
        eventService: EventService,
        priceService: PriceService,
        backFillService: BackFillService,
        positionService: com.beancounter.event.service.PositionService
    ): EventLoaderServiceConfig =
        EventLoaderServiceConfig(
            eventService = eventService,
            priceService = priceService,
            backFillService = backFillService,
            positionService = positionService
        )

    @Bean
    fun eventLoaderAuthConfig(
        dateSplitter: DateSplitter,
        loginService: LoginService
    ): AuthConfig =
        AuthConfig(
            dateSplitter = dateSplitter,
            loginService = loginService
        )

    @Bean
    fun eventLoaderConfig(
        sharedConfig: SharedConfig,
        serviceConfig: EventLoaderServiceConfig,
        authConfig: AuthConfig
    ): EventLoaderConfig =
        EventLoaderConfig(
            sharedConfig = sharedConfig,
            serviceConfig = serviceConfig,
            authConfig = authConfig
        )
}