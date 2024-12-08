package com.beancounter.event.contract

import com.beancounter.common.contracts.Payload
import com.beancounter.common.event.CorporateEvent

/**
 * Response contract containing a collection of corporate events.
 */
data class CorporateEventResponses(
    override var data: Collection<CorporateEvent> = ArrayList(),
) : Payload<Collection<CorporateEvent>>
