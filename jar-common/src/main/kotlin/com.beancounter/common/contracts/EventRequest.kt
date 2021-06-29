package com.beancounter.common.contracts

import com.beancounter.common.event.CorporateEvent
import org.springframework.boot.context.properties.ConstructorBinding

/**
 * Command pattern to transmit a Corporate event.
 */
data class EventRequest @ConstructorBinding constructor(override val data: CorporateEvent) : Payload<CorporateEvent>
