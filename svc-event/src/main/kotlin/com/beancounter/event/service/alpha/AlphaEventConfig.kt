package com.beancounter.event.service.alpha

import com.beancounter.event.service.EventBehaviourFactory
import com.beancounter.event.service.TaxService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * Configuration class to import necessary services.
 */
@Configuration
@Import(
    TaxService::class,
    EventBehaviourFactory::class
)
class AlphaEventConfig {
    @Bean
    fun alphaEventAdapter(
        taxService: TaxService,
        @Value("\${beancounter.events.dividend.days-to-add:${AlphaEventAdapter.DEFAULT_DAYS_TO_ADD}}")
        daysToAdd: Long
    ): AlphaEventAdapter = AlphaEventAdapter(taxService, daysToAdd)
}