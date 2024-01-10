package com.beancounter.marketdata.fx.fxrates

import com.beancounter.common.utils.DateUtils
import java.time.LocalDate

/**
 * Simple helper to assist with the ECB FX Rate provider needs.
 *
 * Today is treated as T-1
 */
class EcbDate(val dateUtils: DateUtils) {
    private fun earliestDate(): LocalDate {
        return dateUtils.getOrThrow(EARLIEST)
    }

    fun getValidDate(tradeDate: String): String {
        if (dateUtils.isToday(tradeDate)) {
            return dateUtils.getDate(dateUtils.offsetDateString(tradeDate))
                .minusDays(1).toString()
        }
        val requestedDate = dateUtils.getOrThrow(tradeDate)
        return if (requestedDate.isBefore(earliestDate())) {
            EARLIEST
        } else {
            tradeDate
        }
    }

    companion object {
        const val EARLIEST = "1999-01-04"
    }
}
