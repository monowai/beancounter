package com.beancounter.position.valuation

import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import java.math.BigDecimal

/**
 * The change on day of every position in [bucket], summed. MarketValue sets a position's
 * gainOnDay only when it is priced, not cash, and has a previous close, so anything else
 * adds zero. The one definition behind the published portfolio figure and the net-worth
 * headline.
 */
fun Positions.gainOnDay(bucket: Position.In): BigDecimal =
    positions.values
        .mapNotNull { it.moneyValues[bucket]?.gainOnDay }
        .fold(BigDecimal.ZERO, BigDecimal::add)