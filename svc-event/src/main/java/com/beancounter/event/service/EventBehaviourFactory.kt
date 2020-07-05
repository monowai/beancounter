package com.beancounter.event.service

import com.beancounter.common.event.CorporateEvent
import com.beancounter.event.service.alpha.AlphaEventAdapter
import org.springframework.stereotype.Service
import java.util.*

@Service
class EventBehaviourFactory {
    var adapters: MutableMap<String, Event> = HashMap()
    fun getAdapter(event: CorporateEvent?): Event? {
        return adapters[event!!.source]
    }

    init {
        adapters["ALPHA"] = AlphaEventAdapter(TaxService())
    }
}