package com.beancounter.event.behaviour

import com.beancounter.event.behaviour.alpha.AlphaEventAdapter
import org.springframework.stereotype.Service

/**
 * Factory for obtain event behaviour providers.
 */
@Service
class EventBehaviourFactory(
    private val alphaEventAdapter: AlphaEventAdapter
) {
    /** The only adapter today; corporate events are all sourced from AlphaVantage. */
    fun getAdapter(): Event = alphaEventAdapter
}