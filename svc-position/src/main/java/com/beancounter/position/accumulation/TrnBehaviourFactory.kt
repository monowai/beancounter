package com.beancounter.position.accumulation

import com.beancounter.common.model.TrnType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.EnumMap

@Service
/**
 * All supported behaviours are can be accessed via this factory. A behaviour is responsible for accumulating a
 * transaction into a position
 */
class TrnBehaviourFactory {
    private val trnBehaviours: MutableMap<TrnType, AccumulationStrategy> = EnumMap(TrnType::class.java)

    @Autowired(required = false)
    fun setBuyBehaviour(buyBehaviour: BuyBehaviour) {
        trnBehaviours[TrnType.BUY] = buyBehaviour
    }

    @Autowired(required = false)
    fun setSellBehaviour(sellBehaviour: SellBehaviour) {
        trnBehaviours[TrnType.SELL] = sellBehaviour
    }

    @Autowired(required = false)
    fun setSplitBehaviour(splitBehaviour: SplitBehaviour) {
        trnBehaviours[TrnType.SPLIT] = splitBehaviour
    }

    @Autowired(required = false)
    fun setDividendBehaviour(dividendBehaviour: DividendBehaviour) {
        trnBehaviours[TrnType.DIVI] = dividendBehaviour
    }

    operator fun get(trnType: TrnType): AccumulationStrategy? {
        return trnBehaviours[trnType]
    }
}
