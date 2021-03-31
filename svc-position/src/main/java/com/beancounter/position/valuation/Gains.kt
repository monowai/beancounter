package com.beancounter.position.valuation

import com.beancounter.common.model.MoneyValues
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
/**
 * Compute a gain from supplied arguments
 */
class Gains {
    /**
     * Compute various gain buckets.
     */
    fun value(total: BigDecimal, moneyValues: MoneyValues) {
        if (total.compareTo(BigDecimal.ZERO) != 0) {
            moneyValues.unrealisedGain = moneyValues.marketValue
                .subtract(moneyValues.costValue)
        }
        moneyValues.totalGain = moneyValues.unrealisedGain
            .add(
                moneyValues.dividends
                    .add(moneyValues.realisedGain)
            )
    }
}
