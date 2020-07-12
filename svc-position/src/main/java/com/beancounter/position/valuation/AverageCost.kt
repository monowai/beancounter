package com.beancounter.position.valuation

import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.utils.MathUtils.Companion.getMathContext
import com.beancounter.common.utils.MathUtils.Companion.multiply
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class AverageCost {
    /**
     * Unit cost calculator.
     *
     * @param costBasis accumulation of all trn values that affect cost
     * @param total     current quantity
     * @return unit cost
     */
    fun value(costBasis: BigDecimal, total: BigDecimal?): BigDecimal {
        return costBasis.divide(total, getMathContext())
    }

    fun setCostValue(position: Position, moneyValues: MoneyValues) {
        val quantityValues = position.quantityValues
        moneyValues.costValue = multiply(moneyValues.averageCost, quantityValues.getTotal())!!
    }
}