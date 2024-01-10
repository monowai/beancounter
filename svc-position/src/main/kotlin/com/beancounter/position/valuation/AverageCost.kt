package com.beancounter.position.valuation

import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.utils.MathUtils.Companion.getMathContext
import com.beancounter.common.utils.MathUtils.Companion.multiply
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Compute the cost using a simple average.
 */
@Service
class AverageCost {
    /**
     * Unit cost calculator.
     *
     * @param costBasis accumulation of all trn values that affect cost
     * @param total     current quantity
     * @return unit cost
     */
    fun value(
        costBasis: BigDecimal,
        total: BigDecimal?,
    ): BigDecimal {
        return costBasis.divide(total, getMathContext())
    }

    fun getCostValue(
        position: Position,
        moneyValues: MoneyValues,
    ): BigDecimal {
        val quantityValues = position.quantityValues
        return multiply(moneyValues.averageCost, quantityValues.getTotal())!!
    }
}
