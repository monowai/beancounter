package com.beancounter.position.accumulation

import com.beancounter.common.model.TrnType
import com.beancounter.position.utils.CurrencyResolver
import com.beancounter.position.valuation.AverageCost
import org.springframework.stereotype.Service

/**
 * Accumulates a REDUCE transaction: a sell without cash impact.
 * Used for reducing positions or adding liabilities without affecting cash positions.
 */
@Service
class ReduceBehaviour(
    currencyResolver: CurrencyResolver = CurrencyResolver(),
    averageCost: AverageCost = AverageCost()
) : DisposalBehaviour(currencyResolver, averageCost) {
    override val supportedType: TrnType
        get() = TrnType.REDUCE
}