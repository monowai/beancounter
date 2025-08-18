package com.beancounter.event.config

import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.utils.DateUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Shared configuration for common dependencies used across multiple services.
 */
data class SharedConfig(
    val dateUtils: DateUtils,
    val portfolioService: PortfolioServiceClient
)

@Configuration
class SharedConfiguration {
    @Bean
    fun sharedConfig(
        dateUtils: DateUtils,
        portfolioService: PortfolioServiceClient
    ): SharedConfig =
        SharedConfig(
            dateUtils = dateUtils,
            portfolioService = portfolioService
        )
}