package com.beancounter.common.utils

import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.TrnType
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
@Import(NumberUtils::class)
/**
 * Service to compute a tradeAmount in various way
 */
class TradeCalculator(val numberUtils: NumberUtils) {
    fun amount(quantity: BigDecimal, price: BigDecimal, fees: BigDecimal = BigDecimal.ZERO): BigDecimal {
        var result: BigDecimal?
        result = MathUtils.multiply(quantity, price)
        if (result != null && !numberUtils.isUnset(fees)) {
            result = result.add(fees)
        }
        return MathUtils.nullSafe(result)
    }

    /**
     * Default algorithm
     */
    fun amount(trnInput: TrnInput): BigDecimal {
        if (numberUtils.isSet(trnInput.tradeAmount)) return trnInput.tradeAmount

        return if (TrnType.isCorporateAction(trnInput.trnType))
            trnInput.tradeAmount
        else
            amount(trnInput.quantity, trnInput.price, trnInput.fees)
    }
}
