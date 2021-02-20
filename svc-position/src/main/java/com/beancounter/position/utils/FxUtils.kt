package com.beancounter.position.utils

import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.model.Currency
import com.beancounter.common.model.IsoCurrencyPair.Companion.toPair
import com.beancounter.common.model.Positions
import com.beancounter.common.utils.DateUtils
import org.springframework.stereotype.Service

@Service
class FxUtils {
    private val dateUtils = DateUtils()
    fun buildRequest(base: Currency?, positions: Positions): FxRequest {
        if (positions.asAt == null) {
            positions.asAt = dateUtils.today()
        }
        val fxRequest = FxRequest(positions.asAt)
        val portfolio = positions.portfolio.currency
        for ((asset) in positions.positions.values) {
            fxRequest.add(
                toPair(
                    base!!,
                    asset.market.currency
                )
            )
            fxRequest.add(
                toPair(
                    portfolio,
                    asset.market.currency
                )
            )
        }
        return fxRequest
    }
}
