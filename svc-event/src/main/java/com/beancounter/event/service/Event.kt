package com.beancounter.event.service

import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position

/**
 * Means to calculate a TRN from a corporate action for a position.
 */
interface Event {
    fun calculate(
        portfolio: Portfolio,
        currentPosition: Position,
        corporateEvent: CorporateEvent
    ): TrustedTrnEvent?
}
