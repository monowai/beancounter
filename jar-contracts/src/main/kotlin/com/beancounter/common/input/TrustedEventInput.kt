package com.beancounter.common.input

import com.beancounter.common.contracts.Payload
import com.beancounter.common.event.CorporateEvent

/**
 * Request to create an Event against a portfolioId.
 */
data class TrustedEventInput(
    override val data: CorporateEvent
) : Payload<CorporateEvent>