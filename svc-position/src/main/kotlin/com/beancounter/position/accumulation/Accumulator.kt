package com.beancounter.position.accumulation

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Service

/**
 * Convenience service to apply the correct AccumulationLogic to the transaction
 * and calculate the value of a position.
 *
 * @author mikeh
 * @since 2019-02-07
 */
@Service
@Import(
    DateUtils::class,
    TrnBehaviourFactory::class,
    BuyBehaviour::class,
    SellBehaviour::class,
    DividendBehaviour::class,
    SplitBehaviour::class
)
class Accumulator(private val trnBehaviourFactory: TrnBehaviourFactory) {
    fun accumulate(trn: Trn, positions: Positions): Position {
        return accumulate(
            trn, positions.portfolio,
            positions[trn.asset, trn.tradeDate]
        )
    }

    /**
     * Main calculation routine.
     *
     * @param trn      Transaction to add
     * @param position Position to accumulate the transaction into
     * @return result object
     */
    fun accumulate(trn: Trn, portfolio: Portfolio, position: Position): Position {
        val dateSensitive = trn.trnType !== TrnType.DIVI
        if (dateSensitive) {
            isDateSequential(trn, position)
        }
        val accumulationStrategy = trnBehaviourFactory[trn.trnType]!!
        accumulationStrategy.accumulate(trn, portfolio, position)
        position.dateValues.last = trn.tradeDate
        return position
    }

    private fun isDateSequential(trn: Trn, position: Position) {
        var validDate = false
        val tradeDate = trn.tradeDate
        val positionDate = position.dateValues.last
        if (positionDate == null || positionDate <= tradeDate) {
            validDate = true
        }
        if (!validDate) {
            throw BusinessException(
                String.format(
                    "Date sequence problem %s",
                    trn.toString()
                )
            )
        }
    }
}
