package com.beancounter.position.accumulation

import com.beancounter.common.model.TrnType
import com.beancounter.position.marketvalue.AverageCost
import org.springframework.stereotype.Service

/**
 * Accumulates a sell transaction. Proceeds reach cash through TrnType.creditsCash.
 */
@Service
class SellBehaviour(
    currencyResolver: CurrencyResolver = CurrencyResolver(),
    averageCost: AverageCost = AverageCost()
) : DisposalBehaviour(currencyResolver, averageCost) {
    override val supportedType: TrnType
        get() = TrnType.SELL
}