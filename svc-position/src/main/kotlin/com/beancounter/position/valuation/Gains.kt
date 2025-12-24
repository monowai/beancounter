package com.beancounter.position.valuation

import com.beancounter.common.model.MoneyValues
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Compute a gain from supplied arguments
 */
@Service
class Gains {
    /**
     * Compute various gain buckets.
     */
    fun value(
        total: BigDecimal,
        moneyValues: MoneyValues
    ) {
        moneyValues.unrealisedGain =
            if (total.signum() != 0) {
                moneyValues.marketValue.subtract(moneyValues.costValue)
            } else {
                // Closed position - no unrealised gain possible
                BigDecimal.ZERO
            }
        moneyValues.totalGain =
            moneyValues.unrealisedGain.add(moneyValues.dividends).add(moneyValues.realisedGain)
    }
}