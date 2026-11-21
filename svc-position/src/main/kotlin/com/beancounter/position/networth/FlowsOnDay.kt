package com.beancounter.position.networth

import com.beancounter.common.model.DayFlows
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.performance.TwrBoundary
import com.beancounter.position.performance.externalFlowIn
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * A portfolio's external cash flows on the valuation date, read from the trns its
 * positions are built from, so the Net Worth headline can show the day's cash flow
 * beside the market move without another read of bc-data.
 *
 * An external flow is what time-weighted return treats as one
 * ([TwrBoundary.isExternalCashFlow]), signed and sized by [externalFlowIn].
 */
@Component
class FlowsOnDay(
    private val dateUtils: DateUtils
) {
    /**
     * [trns]' external flows dated [asAt] ("today" resolves through [DateUtils]), in
     * [portfolio]'s currency: money in summed into the inflow, money out into the outflow.
     */
    fun of(
        portfolio: Portfolio,
        trns: Collection<Trn>,
        asAt: String
    ): DayFlows {
        if (trns.isEmpty()) return DayFlows.NONE
        val day = dateUtils.getFormattedDate(asAt)
        var inflow = BigDecimal.ZERO
        var outflow = BigDecimal.ZERO
        trns
            .filter { it.tradeDate == day && TwrBoundary.isExternalCashFlow(it.trnType) }
            .map { it.externalFlowIn(portfolio) }
            .forEach { amount ->
                if (amount.signum() > 0) inflow = inflow.add(amount) else outflow = outflow.add(amount)
            }
        return DayFlows(inflow, outflow)
    }
}