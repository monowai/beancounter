package com.beancounter.common.input

import com.beancounter.common.contracts.Payload
import com.beancounter.common.event.CorporateEvent

data class TrustedEventInput constructor(override val data: CorporateEvent)  : Payload<CorporateEvent>