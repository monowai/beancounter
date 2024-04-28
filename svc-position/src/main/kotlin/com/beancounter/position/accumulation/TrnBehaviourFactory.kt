package com.beancounter.position.accumulation

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.TrnType
import org.springframework.beans.factory.annotation.Autowired
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
    DepositBehaviour::class,
    WithdrawalBehaviour::class,
    DividendBehaviour::class,
    CashAccumulator::class,
    SplitBehaviour::class,
    FxBuyBehaviour::class,
    BalanceBehaviour::class,
)
@Service
class TrnBehaviourFactory {
    private val trnBehaviours: MutableMap<TrnType, AccumulationStrategy> = EnumMap(TrnType::class.java)

    @Autowired(required = false)
    fun setBuyBehaviour(behaviour: BuyBehaviour) {
        trnBehaviours[TrnType.BUY] = behaviour
    }

    @Autowired
    fun setWithdrawalBehaviour(behaviour: WithdrawalBehaviour) {
        trnBehaviours[TrnType.WITHDRAWAL] = behaviour
    }

    @Autowired
    fun setDepositBehaviour(behaviour: DepositBehaviour) {
        trnBehaviours[TrnType.DEPOSIT] = behaviour
    }

    @Autowired(required = false)
    fun setSellBehaviour(behaviour: SellBehaviour) {
        trnBehaviours[TrnType.SELL] = behaviour
    }

    @Autowired(required = false)
    fun setFxBuyBehaviour(behaviour: FxBuyBehaviour) {
        trnBehaviours[TrnType.FX_BUY] = behaviour
    }

    @Autowired(required = false)
    fun setSplitBehaviour(behaviour: SplitBehaviour) {
        trnBehaviours[TrnType.SPLIT] = behaviour
    }

    @Autowired(required = false)
    fun setDividendBehaviour(behaviour: DividendBehaviour) {
        trnBehaviours[TrnType.DIVI] = behaviour
    }

    @Autowired(required = false)
    fun setBalanceBehaviour(behaviour: BalanceBehaviour) {
        trnBehaviours[TrnType.BALANCE] = behaviour
    }

    @Autowired(required = false)
    fun setAddBehaviour(behaviour: BuyBehaviour) {
        trnBehaviours[TrnType.ADD] = behaviour
    }

    operator fun get(trnType: TrnType): AccumulationStrategy {
        if (!trnBehaviours.containsKey(trnType)) {
            throw BusinessException("Unsupported TrnType $trnType")
        }
        return trnBehaviours[trnType]!!
    }
}
