package com.beancounter.event.service.alpha

import com.beancounter.event.service.EventBehaviourFactory
import com.beancounter.event.service.TaxService
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(TaxService::class, AlphaEventAdapter::class, EventBehaviourFactory::class)
class AlphaEventConfig
