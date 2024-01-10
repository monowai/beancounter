package com.beancounter.position.utils

import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.model.Currency
import com.beancounter.common.model.IsoCurrencyPair.Companion.toPair
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import org.springframework.stereotype.Service

/**
 * Helper to build an FxRequest from the supplied arguments.
 */
@Service
class FxUtils {
    fun buildRequest(
        base: Currency,
        positions: Positions,
    ): FxRequest {
        val fxRequest = FxRequest(positions.asAt)
        val portfolio = positions.portfolio.currency
        for (position in positions.positions.values) {
            fxRequest.add(
                toPair(
                    position.getMoneyValues(Position.In.TRADE).currency,
                    base,
                ),
            )
            fxRequest.add(
                toPair(
                    position.getMoneyValues(Position.In.TRADE).currency,
                    portfolio,
                ),
            )
        }
        return fxRequest
    }
}
