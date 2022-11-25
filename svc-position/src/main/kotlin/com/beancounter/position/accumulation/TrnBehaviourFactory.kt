package com.beancounter.position.accumulation

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.TrnType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Service
import java.util.EnumMap

/**
 * All supported behaviours are can be accessed via this factory. A behaviour is responsible for accumulating a
 * transaction into a position
 */
@Import(
    BuyBehaviour::class,
    SellBehaviour::class,
    DepositBehaviour::class,
    WithdrawalBehaviour::class,
    DividendBehaviour::class,
    SplitBehaviour::class,
    FxBuyBehaviour::class
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

    operator fun get(trnType: TrnType): AccumulationStrategy {
        if (!trnBehaviours.containsKey(trnType)) {
            throw BusinessException("Unsupported TrnType $trnType")
        }
        return trnBehaviours[trnType]!!
    }
}
