package com.beancounter.position.accumulation

import IrrCalculator
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.model.TrnType.BALANCE
import com.beancounter.common.model.TrnType.DEPOSIT
import com.beancounter.common.model.TrnType.DIVI
import com.beancounter.common.model.TrnType.FX_BUY
import com.beancounter.common.model.TrnType.WITHDRAWAL
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.utils.CurrencyResolver
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Service

/**
 * Service to apply transaction-specific accumulation logic to positions,
 * updating their state based on the effects of each transaction.
 * @author mikeh
 * @since 2019-02-07
 */
@Service
@Import(
    DateUtils::class,
    TrnBehaviourFactory::class,
    CurrencyResolver::class,
    IrrCalculator::class,
)
class Accumulator(private val trnBehaviourFactory: TrnBehaviourFactory) {
    private val cashSet = setOf(BALANCE, FX_BUY, DEPOSIT, WITHDRAWAL)

    /**
     * Processes a transaction and updates the corresponding position.
     * Cash transactions are handled separately to ensure accurate financial tracking.
     *
     * @param trn The transaction to be processed.
     * @param positions Collection of all positions.
     * @return Updated position based on the transaction.
     */
    fun accumulate(
        trn: Trn,
        positions: Positions,
    ): Position {
        val position = positions.getOrCreate(trn.asset, trn.tradeDate)
        if (trn.trnType !== DIVI) {
            ensureDateSequential(trn, position)
        }
        trnBehaviourFactory[trn.trnType].accumulate(trn, positions)
        position.dateValues.last = trn.tradeDate

        if (isCash(trn)) {
            accumulateCash(trn, positions)
        }
        position.periodicCashFlows.add(trn)

        return position
    }

    /**
     * Determines if cash accumulation should occur for a given transaction.
     *
     * @param trn The transaction to evaluate.
     * @return True if cash should be accumulated, false otherwise.
     */
    private fun isCash(trn: Trn): Boolean =
        trn.cashAsset != null && TrnType.isCashImpacted(trn.trnType) &&
            trn.trnType !in cashSet

    /**
     * Accumulates cash-related transactions to the corresponding position based on the transaction type.
     *
     * @param trn The transaction to be processed.
     * @param positions The collection of all positions to update.
     */
    private fun accumulateCash(
        trn: Trn,
        positions: Positions,
    ) {
        val cashPosition = positions.getOrCreate(trn.cashAsset!!, trn.tradeDate)

        when {
            TrnType.isCashCredited(trn.trnType) -> trnBehaviourFactory[DEPOSIT].accumulate(trn, positions, cashPosition)
            TrnType.isCashDebited(trn.trnType) -> trnBehaviourFactory[WITHDRAWAL].accumulate(trn, positions, cashPosition)
        }

        cashPosition.dateValues.last = trn.tradeDate
    }

    /**
     * Ensures that the transaction date is sequential and not earlier than the last recorded position date.
     *
     * @param trn The transaction to check.
     * @param position The position associated with the transaction.
     * @throws BusinessException if the transaction date is before the last recorded date of the position.
     */
    private fun ensureDateSequential(
        trn: Trn,
        position: Position,
    ) {
        val tradeDate = trn.tradeDate
        val positionDate = position.dateValues.last

        if (positionDate != null && tradeDate < positionDate) {
            throw BusinessException("Date sequence problem for transaction on ${trn.tradeDate} with last position date on $positionDate")
        }
    }
}
