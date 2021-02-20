package com.beancounter.event.contract

import com.beancounter.common.contracts.Payload
import com.beancounter.common.event.CorporateEvent

data class CorporateEventsResponse(override var data: Collection<CorporateEvent> = ArrayList()) :
    Payload<Collection<CorporateEvent>>
