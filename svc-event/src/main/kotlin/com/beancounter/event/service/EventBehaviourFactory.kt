package com.beancounter.event.service

import com.beancounter.event.service.alpha.AlphaEventAdapter
import org.springframework.stereotype.Service

/**
 * Factory for obtain event behaviour providers.
 */
@Service
class EventBehaviourFactory {
    private val alphaEventAdapter = AlphaEventAdapter(TaxService())

    /** The only adapter today; corporate events are all sourced from AlphaVantage. */
    fun getAdapter(): Event = alphaEventAdapter
}