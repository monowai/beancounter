package com.beancounter.position.accumulation

import com.beancounter.common.model.TrnType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.EnumMap

@Service
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
    fun setSplitBehaviour(splitBeahviour: SplitBehaviour) {
        trnBehaviours[TrnType.SPLIT] = splitBeahviour
    }

    @Autowired(required = false)
    fun setDividendBehaviour(dividendBeahviour: DividendBehaviour) {
        trnBehaviours[TrnType.DIVI] = dividendBeahviour
    }

    operator fun get(trnType: TrnType?): AccumulationStrategy? {
        return trnBehaviours[trnType]
    }
}
