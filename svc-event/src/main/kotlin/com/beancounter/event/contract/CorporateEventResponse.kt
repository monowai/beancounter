package com.beancounter.event.contract

import com.beancounter.common.contracts.Payload
import com.beancounter.common.event.CorporateEvent

/**
 * Payload representation of a Corporate Action event.
 */
data class CorporateEventResponse(override val data: CorporateEvent) : Payload<CorporateEvent>
