package com.beancounter.common.contracts

import com.beancounter.common.event.CorporateEvent

/**
 * Command pattern to transmit a Corporate event.
 */
data class EventRequest constructor(override val data: CorporateEvent) : Payload<CorporateEvent>
