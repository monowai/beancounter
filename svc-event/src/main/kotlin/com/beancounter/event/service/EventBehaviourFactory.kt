package com.beancounter.event.service

import com.beancounter.common.event.CorporateEvent
import com.beancounter.event.service.alpha.AlphaEventAdapter
import org.springframework.stereotype.Service

/**
 * Factory for obtain event behaviour providers.
 */
@Service
class EventBehaviourFactory {
    // final var adapters: MutableMap<String, Event> = HashMap()
    private final val alphaEventAdapter = AlphaEventAdapter(TaxService())

    fun getAdapter(event: CorporateEvent): Event {
        return alphaEventAdapter // We only have one adapter
    }
}