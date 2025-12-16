package com.beancounter.position.accumulation

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.TrnType
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Service
import java.util.EnumMap

/**
 * A factory class that manages the instantiation and retrieval of transaction accumulation strategies.
 * Each transaction type is associated with a specific strategy that encapsulates the logic for processing
 * and accumulating transactions into financial positions.
 *
 * This class dynamically handles the association between transaction types and their corresponding behaviors,
 * allowing for flexible insertion of new behaviors without modifying the client code. It supports a range of
 * transaction types, including buys, sells, deposits, withdrawals, and more, each managed by a specialized
 * behavior class.
 *
 * @throws BusinessException if an unsupported transaction type is requested.
 */
@Import(
    BuyBehaviour::class,
    SellBehaviour::class,
    ReduceBehaviour::class,
    DepositBehaviour::class,
    WithdrawalBehaviour::class,
    IncomeBehaviour::class,
    DeductionBehaviour::class,
    DividendBehaviour::class,
    CashAccumulator::class,
    SplitBehaviour::class,
    FxBuyBehaviour::class,
    BalanceBehaviour::class
)
@Service
class TrnBehaviourFactory(
    strategies: List<AccumulationStrategy>
) {
    private val trnBehaviours: MutableMap<TrnType, AccumulationStrategy> =
        EnumMap(TrnType::class.java)

    init {
        strategies.forEach { strategy ->
            trnBehaviours[strategy.supportedType] = strategy
            // Specifically assign BuyBehaviour to ADD TrnType
            if (strategy.supportedType == TrnType.BUY) {
                trnBehaviours[TrnType.ADD] = strategy
            }
        }
    }

    operator fun get(trnType: TrnType): AccumulationStrategy =
        trnBehaviours[trnType] ?: throw BusinessException("Unsupported TrnType $trnType")
}