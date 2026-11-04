package com.beancounter.event.backfill

import com.beancounter.auth.client.LoginService
import com.beancounter.client.services.PriceService
import com.beancounter.event.config.SharedConfig
import com.beancounter.event.corporate.CorporateEventStore
import com.beancounter.event.distribution.PositionService
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
    val eventStore: CorporateEventStore,
    val priceService: PriceService,
    val backFillService: BackFillService,
    val positionService: PositionService
)

data class AuthConfig(
    val dateSplitter: DateSplitter,
    val loginService: LoginService
)

@Configuration
class EventLoaderConfiguration {
    @Bean
    fun eventLoaderServiceConfig(
        eventStore: CorporateEventStore,
        priceService: PriceService,
        backFillService: BackFillService,
        positionService: PositionService
    ): EventLoaderServiceConfig =
        EventLoaderServiceConfig(
            eventStore = eventStore,
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