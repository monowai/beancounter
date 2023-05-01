package com.beancounter.common.utils

import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.TrnType
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Service
import java.math.BigDecimal
import kotlin.math.sign

/**
 * Service to compute a tradeAmount in various way
 */
@Service
@Import(NumberUtils::class)
class TradeCalculator(val numberUtils: NumberUtils) {
    fun amount(quantity: BigDecimal, price: BigDecimal, fees: BigDecimal = BigDecimal.ZERO): BigDecimal {
        var result: BigDecimal?
        result = MathUtils.multiplyAbs(quantity, price)
        if (!numberUtils.isUnset(fees)) {
            result = result.add(fees)
        }
        return MathUtils.nullSafe(result)
    }

    /**
     * Default algorithm
     */
    fun amount(trnInput: TrnInput): BigDecimal {
        if (numberUtils.isSet(trnInput.tradeAmount)) return sign(trnInput.tradeAmount, trnInput.trnType)
        return amount(trnInput.quantity, trnInput.price, trnInput.fees)
    }

    fun sign(tradeAmount: BigDecimal, trnType: TrnType): BigDecimal {
        return if (trnType == TrnType.REDUCE) {
            BigDecimal.ZERO - tradeAmount.abs()
        } else {
            tradeAmount.abs()
        }
    }
}
